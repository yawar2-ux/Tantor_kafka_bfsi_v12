package client

import (
	"bytes"
	"crypto/tls"
	"crypto/x509"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"os"
	"time"

	"io.translab/tantor-agent/internal/config"
	"io.translab/tantor-agent/pkg/api"
)

const (
	agentTokenHeader = "X-Tantor-Agent-Token"
	agentHostHeader  = "X-Tantor-Agent-Host"
)

// APIClient handles communication with the Tantor Server
type APIClient struct {
	cfg        *config.Config
	httpClient *http.Client
}

func NewAPIClient(cfg *config.Config) (*APIClient, error) {
	var certificates []tls.Certificate
	if cfg.Agent.CertFile != "" && cfg.Agent.KeyFile != "" {
		cert, err := tls.LoadX509KeyPair(cfg.Agent.CertFile, cfg.Agent.KeyFile)
		if err != nil {
			return nil, fmt.Errorf("failed to load client certificate/key: %w", err)
		}
		certificates = append(certificates, cert)
	}

	caCertPool := x509.NewCertPool()
	if cfg.Agent.CACert != "" {
		caCert, err := os.ReadFile(cfg.Agent.CACert)
		if err != nil {
			return nil, fmt.Errorf("failed to read CA certificate: %w", err)
		}
		if ok := caCertPool.AppendCertsFromPEM(caCert); !ok {
			return nil, fmt.Errorf("failed to parse CA certificate: %s", cfg.Agent.CACert)
		}
	}

	tlsConfig := &tls.Config{
		Certificates:       certificates,
		RootCAs:            caCertPool,
		InsecureSkipVerify: cfg.Agent.InsecureSkipVerify, // dev-only escape hatch; keep false in prod
		MinVersion:         tls.VersionTLS12,
	}

	transport := &http.Transport{TLSClientConfig: tlsConfig}
	client := &http.Client{
		Transport: transport,
		Timeout:   10 * time.Minute,
	}

	return &APIClient{cfg: cfg, httpClient: client}, nil
}

func (c *APIClient) RegisterHost(req *api.HostRegistration) error {
	return c.post("/api/v1/agents/register", req, nil)
}

func (c *APIClient) SendHeartbeat(hb *api.HostHeartbeat) error {
	return c.post("/api/v1/agents/heartbeat", hb, nil)
}

func (c *APIClient) PollTasks() ([]api.Task, error) {
	var tasks []api.Task
	url := fmt.Sprintf("%s/api/v1/agents/%s/tasks", c.cfg.Agent.ServerURL, c.cfg.Agent.HostID)

	req, err := http.NewRequest(http.MethodGet, url, nil)
	if err != nil {
		return nil, err
	}
	c.addAuthHeaders(req)

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	if resp.StatusCode == http.StatusNoContent {
		return tasks, nil // No tasks
	}
	if resp.StatusCode != http.StatusOK {
		bodyBytes, _ := io.ReadAll(resp.Body)
		return nil, fmt.Errorf("unexpected status %d: %s", resp.StatusCode, string(bodyBytes))
	}
	if err := json.NewDecoder(resp.Body).Decode(&tasks); err != nil {
		return nil, err
	}
	return tasks, nil
}

func (c *APIClient) ReportTaskResult(result *api.TaskResult) error {
	return c.post("/api/v1/agents/tasks/result", result, nil)
}

func (c *APIClient) ReportTaskStep(step *api.TaskStepReport) error {
	return c.post("/api/v1/agents/tasks/step", step, nil)
}

func (c *APIClient) DownloadArtifact(url, destPath string) (string, error) {
	req, err := http.NewRequest(http.MethodGet, url, nil)
	if err != nil {
		return "", err
	}
	c.addAuthHeaders(req)

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return "", err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		bodyBytes, _ := io.ReadAll(resp.Body)
		return "", fmt.Errorf("failed to download artifact, status %d: %s", resp.StatusCode, string(bodyBytes))
	}

	out, err := os.Create(destPath)
	if err != nil {
		return "", err
	}
	defer out.Close()

	if _, err = io.Copy(out, resp.Body); err != nil {
		return "", err
	}

	checksum := resp.Header.Get("X-Checksum-SHA256")
	return checksum, nil
}

func (c *APIClient) post(path string, reqBody interface{}, respBody interface{}) error {
	data, err := json.Marshal(reqBody)
	if err != nil {
		return err
	}

	url := c.cfg.Agent.ServerURL + path
	req, err := http.NewRequest(http.MethodPost, url, bytes.NewBuffer(data))
	if err != nil {
		return err
	}
	req.Header.Set("Content-Type", "application/json")
	c.addAuthHeaders(req)

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()

	if resp.StatusCode >= 400 {
		bodyBytes, _ := io.ReadAll(resp.Body)
		return fmt.Errorf("API error %d: %s", resp.StatusCode, string(bodyBytes))
	}
	if respBody != nil {
		return json.NewDecoder(resp.Body).Decode(respBody)
	}
	return nil
}

func (c *APIClient) addAuthHeaders(req *http.Request) {
	if c.cfg.Agent.AgentToken != "" {
		req.Header.Set(agentTokenHeader, c.cfg.Agent.AgentToken)
	}
	if c.cfg.Agent.HostID != "" {
		req.Header.Set(agentHostHeader, c.cfg.Agent.HostID)
	}
}

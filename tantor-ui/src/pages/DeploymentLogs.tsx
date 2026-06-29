import { useState, useEffect, useRef } from 'react';
import { useParams } from 'react-router-dom';
import { Terminal, Clock, CheckCircle, XCircle, Loader2, Server, Layers, RotateCcw } from 'lucide-react';
import './DeploymentLogs.css';

interface Task {
  id: string;
  hostId: string;
  command: string;
  status: string;
  logOutput: string;
  logFilePath?: string;
  errorMsg: string;
  currentStepName?: string;
  createdAt: string;
  updatedAt: string;
}

interface Job {
  id: string;
  jobType: string;
  status: string;
  requestedBy?: string;
  approvedBy?: string;
  currentStep?: string;
  currentHostId?: string;
  totalSteps: number;
  completedSteps: number;
  failedSteps: number;
  progressPercentage: number;
  failureReason?: string;
  rollbackAvailable?: boolean;
  rollbackStatus?: string;
  createdAt: string;
  updatedAt?: string;
  startedAt?: string;
  finishedAt?: string;
}

interface JobStep {
  id: string;
  jobId: string;
  taskId?: string;
  stepOrder: number;
  stepCode: string;
  stepName: string;
  hostId?: string;
  component?: string;
  status: string;
  startedAt?: string;
  finishedAt?: string;
  durationSeconds?: number;
  retryCount?: number;
  errorMessage?: string;
  logFilePath?: string;
  logExcerpt?: string;
  rollbackStepAvailable?: boolean;
}

interface JobDetails extends Job {
  steps: JobStep[];
  tasks: Task[];
}

interface ClusterInfo {
  id: string;
  status: string;
}

export function DeploymentLogs() {
  const { id } = useParams<{ id: string }>();
  const [cluster, setCluster] = useState<ClusterInfo | null>(null);
  const [jobs, setJobs] = useState<Job[]>([]);
  const [selectedJobId, setSelectedJobId] = useState<string | null>(null);
  const [jobDetails, setJobDetails] = useState<JobDetails | null>(null);
  const [tasks, setTasks] = useState<Task[]>([]);
  const [selectedTaskId, setSelectedTaskId] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const logsEndRef = useRef<HTMLDivElement>(null);

  const fetchData = async () => {
    try {
      const clusterRes = await fetch(`/api/v1/ui/clusters/${id}`);
      if (clusterRes.ok) setCluster(await clusterRes.json());

      const jobsRes = await fetch(`/api/v1/ui/clusters/${id}/jobs`);
      if (jobsRes.ok) {
        const data: Job[] = await jobsRes.json();
        setJobs(data);
        const nextJobId = selectedJobId || data[0]?.id || null;
        setSelectedJobId(nextJobId);
        if (nextJobId) {
          const detailRes = await fetch(`/api/v1/ui/clusters/${id}/jobs/${nextJobId}`);
          if (detailRes.ok) setJobDetails(await detailRes.json());
        }
      }

      const tasksRes = await fetch(`/api/v1/ui/clusters/${id}/tasks`);
      if (tasksRes.ok) {
        const data: Task[] = await tasksRes.json();
        setTasks(data);
        if (data.length > 0 && !selectedTaskId) setSelectedTaskId(data[0].id);
      }
    } catch (e) {
      console.error(e);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchData();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id, selectedJobId]);

  useEffect(() => {
    if (!cluster) return;
    const activeStatuses = ['PENDING', 'DEPLOYING', 'IN_PROGRESS', 'RUNNING', 'VALIDATING', 'DELETING'];
    let interval: ReturnType<typeof setInterval> | undefined;
    if (activeStatuses.includes(String(cluster.status || '').toUpperCase())) {
      interval = setInterval(fetchData, 3000);
    }
    return () => {
      if (interval) clearInterval(interval);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [cluster, id, selectedJobId]);

  useEffect(() => {
    logsEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [jobDetails, tasks, selectedTaskId]);

  const getStatusIcon = (status: string) => {
    switch (String(status || '').toUpperCase()) {
      case 'SUCCESS': return <CheckCircle size={16} className="text-green" />;
      case 'FAILED': return <XCircle size={16} className="text-red" />;
      case 'RUNNING':
      case 'IN_PROGRESS':
      case 'VALIDATING':
      case 'DEPLOYING':
      case 'PENDING': return <Loader2 size={16} className="spin text-blue" />;
      default: return <Clock size={16} className="text-gray" />;
    }
  };

  if (loading && jobs.length === 0 && tasks.length === 0) {
    return <div className="state-center"><Loader2 className="spin" /> Loading deployment jobs...</div>;
  }

  if (jobs.length === 0 && tasks.length === 0) {
    return (
      <div className="empty-state">
        <Terminal size={48} />
        <h3>No Deployment Logs</h3>
        <p>There are no jobs or tasks executed for this cluster yet.</p>
      </div>
    );
  }

  const selectedTask = tasks.find(t => t.id === selectedTaskId) || tasks[0];
  const steps = jobDetails?.steps || [];
  const failedStep = steps.find(s => String(s.status).toUpperCase() === 'FAILED');

  return (
    <div className="deployment-logs-container animate-fade-in">
      <div className="task-sidebar">
        <h3>Job Timeline</h3>
        <div className="task-list">
          {jobs.map(job => (
            <div
              key={job.id}
              className={`task-item ${job.id === selectedJobId ? 'active' : ''} ${job.status === 'FAILED' ? 'failed' : ''}`}
              onClick={() => setSelectedJobId(job.id)}
            >
              <div className="task-item-header">
                <span className="task-command"><Layers size={14} /> {job.jobType}</span>
                {getStatusIcon(job.status)}
              </div>
              <div className="task-item-meta">
                <span>{job.progressPercentage || 0}% complete</span>
                <span>{new Date(job.createdAt).toLocaleTimeString()}</span>
              </div>
            </div>
          ))}
        </div>

        {tasks.length > 0 && (
          <>
            <h3 style={{ marginTop: 20 }}>Legacy Tasks</h3>
            <div className="task-list">
              {tasks.map(task => (
                <div
                  key={task.id}
                  className={`task-item ${task.id === selectedTaskId ? 'active' : ''} ${task.status === 'FAILED' ? 'failed' : ''}`}
                  onClick={() => setSelectedTaskId(task.id)}
                >
                  <div className="task-item-header">
                    <span className="task-command">{task.command}</span>
                    {getStatusIcon(task.status)}
                  </div>
                  <div className="task-item-meta">
                    <span><Server size={12} /> {task.hostId}</span>
                    <span>{new Date(task.createdAt).toLocaleTimeString()}</span>
                  </div>
                </div>
              ))}
            </div>
          </>
        )}
      </div>

      <div className="task-details-pane">
        {jobDetails && (
          <div className="task-summary glass-panel">
            <div className="summary-grid">
              <div className="summary-item"><label>Job Type</label><div>{jobDetails.jobType}</div></div>
              <div className="summary-item"><label>Status</label><div className={`status-text ${String(jobDetails.status).toLowerCase()}`}>{getStatusIcon(jobDetails.status)} {jobDetails.status}</div></div>
              <div className="summary-item"><label>Current Step</label><div>{jobDetails.currentStep || '-'}</div></div>
              <div className="summary-item"><label>Current Host</label><div>{jobDetails.currentHostId || '-'}</div></div>
              <div className="summary-item"><label>Progress</label><div>{jobDetails.completedSteps}/{jobDetails.totalSteps} steps ({jobDetails.progressPercentage || 0}%)</div></div>
              <div className="summary-item"><label>Rollback</label><div>{jobDetails.rollbackAvailable ? jobDetails.rollbackStatus || 'Available' : 'Not available'}</div></div>
            </div>

            {jobDetails.failureReason && <div className="task-error-alert"><strong>Failure:</strong> {jobDetails.failureReason}</div>}
            {failedStep && <div className="task-error-alert"><strong>Failed Step:</strong> {failedStep.stepName} on {failedStep.hostId} — {failedStep.errorMessage}</div>}
          </div>
        )}

        {steps.length > 0 && (
          <div className="task-summary glass-panel" style={{ marginTop: 16 }}>
            <h3>Step-Level Deployment Tracking</h3>
            <div className="step-table-wrapper">
              <table className="step-table">
                <thead><tr><th>#</th><th>Host</th><th>Step</th><th>Component</th><th>Status</th><th>Rollback</th><th>Duration</th></tr></thead>
                <tbody>
                  {steps.map(step => (
                    <tr key={step.id} className={String(step.status).toLowerCase()}>
                      <td>{step.stepOrder}</td>
                      <td>{step.hostId || '-'}</td>
                      <td>{step.stepName}</td>
                      <td>{step.component || '-'}</td>
                      <td>{getStatusIcon(step.status)} {step.status}</td>
                      <td>{step.rollbackStepAvailable ? <RotateCcw size={14} /> : '-'}</td>
                      <td>{step.durationSeconds != null ? `${step.durationSeconds}s` : '-'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}

        {selectedTask && (
          <div className="terminal-container" style={{ marginTop: 16 }}>
            <div className="terminal-header">
              <div className="terminal-dots"><span></span><span></span><span></span></div>
              <div className="terminal-title">Task Log Output {selectedTask.logFilePath ? `— ${selectedTask.logFilePath}` : ''}</div>
            </div>
            <div className="terminal-body">
              <pre>{selectedTask.logOutput || jobDetails?.failureReason || 'Waiting for logs...'}</pre>
              <div ref={logsEndRef} />
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

package sample.fhir.server.jersey.controller;

import ca.uhn.fhir.batch2.api.IJobCoordinator;
import ca.uhn.fhir.batch2.model.JobInstance;
import ca.uhn.fhir.jpa.dao.IBatch2JobInstanceRepository;
import ca.uhn.fhir.jpa.entity.Batch2JobInstanceEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.*;

@RestController
@RequestMapping("/demo")
public class Batch2DemoController {

	@Autowired(required = false)
	private IBatch2JobInstanceRepository myJobInstanceRepository;

	@Autowired(required = false)
	private IJobCoordinator myJobCoordinator;

	@GetMapping(value = "/", produces = "text/html")
	@Transactional(readOnly = true)
	public String index() {
		long jobCount = 0;
		if (myJobInstanceRepository != null) {
			jobCount = myJobInstanceRepository.count();
		}
		return """
				<html>
				<head><title>Batch2 Demo</title>
				<style>
					body { font-family: Arial, sans-serif; margin: 40px; }
					h1 { color: #333; }
					ul { list-style-type: none; padding: 0; }
					li { margin: 10px 0; }
					a { color: #0066cc; text-decoration: none; }
					a:hover { text-decoration: underline; }
					button { margin: 2px; padding: 5px 10px; }
					table { border-collapse: collapse; width: 100%%; }
					th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }
					th { background-color: #f2f2f2; }
				</style>
				</head>
				<body>
				<h1>HAPI FHIR Batch2 Demo Server</h1>
				<p>Server running on port 8080 with H2 in-memory database</p>
				<p>Total jobs in database: %d</p>
				<h2>Demo Actions:</h2>
				<ul>
					<li><a href="/demo/create-job">Create Test Job</a> - Insert a test Batch2 job into the database</li>
					<li><a href="/demo/list-jobs">List All Jobs</a> - View all Batch2 jobs</li>
					<li><a href="/fhir/metadata">FHIR CapabilityStatement</a> - View server metadata</li>
				</ul>
				<h2>FHIR Batch2 Operations:</h2>
				<ul>
					<li>GET <a href="/fhir/$list">/fhir/$list</a> - List jobs via FHIR operation</li>
					<li>POST /fhir/$pause?jobId={id} - Pause a job</li>
					<li>POST /fhir/$resume?jobId={id} - Resume a job</li>
					<li>POST /fhir/$cancel?jobId={id} - Cancel a job</li>
					<li>GET /fhir/$hapi.fhir.batch2-job-history?jobId={id} - Get job history</li>
				</ul>
				</body>
				</html>
				""".formatted(jobCount);
	}

	@GetMapping("/create-job")
	@Transactional
	public String createJob() {
		try {
			Batch2JobInstanceEntity job = new Batch2JobInstanceEntity();
			job.setJobDefinitionId("test-batch2-job");
			job.setJobDefinitionVersion(1);
			job.setStatus(ca.uhn.fhir.batch2.model.JobStatus.QUEUED);
			job.setCreateTime(new Date());
			job.setUpdateTime(new Date());
			job.setParams("{\"resourceType\":\"Patient\",\"count\":100}");
			job.setInstanceId(UUID.randomUUID().toString());
			job.setCurrentGatedStepId("initial-step");
			job.setFastTracking(false);

			myJobInstanceRepository.save(job);

			return """
					<html>
					<body>
					<h2>Job Created Successfully</h2>
					<p><strong>Job ID:</strong> %s</p>
					<p><strong>Status:</strong> QUEUED</p>
					<p><strong>Definition:</strong> test-batch2-job</p>
					<hr/>
					<a href="/demo/list-jobs">View All Jobs</a> |
					<a href="/demo/">Home</a>
					</body>
					</html>
					""".formatted(job.getInstanceId());
		} catch (Exception e) {
			return """
					<html>
					<body>
					<h2>Error Creating Job</h2>
					<p>%s</p>
					<p>%s</p>
					<hr/>
					<a href="/demo/">Home</a>
					</body>
					</html>
					""".formatted(e.getMessage(), Arrays.toString(e.getStackTrace()));
		}
	}

	@GetMapping("/list-jobs")
	@Transactional(readOnly = true)
	public String listJobs() {
		try {
			Page<Batch2JobInstanceEntity> jobs = myJobInstanceRepository.findAll(PageRequest.of(0, 100));

			StringBuilder html = new StringBuilder();
			html.append("<html><body>");
			html.append("<h2>Batch2 Jobs</h2>");
			html.append("<p>Total jobs: ").append(jobs.getTotalElements()).append("</p>");
			html.append("<table><tr><th>Job ID</th><th>Definition</th><th>Status</th><th>Created</th><th>Actions</th></tr>");

			for (Batch2JobInstanceEntity job : jobs.getContent()) {
				html.append("<tr>");
				html.append("<td>").append(truncateId(job.getInstanceId())).append("</td>");
				html.append("<td>").append(job.getJobDefinitionId()).append("</td>");
				html.append("<td>").append(job.getStatus()).append("</td>");
				html.append("<td>").append(job.getCreateTime()).append("</td>");
				html.append("<td>");
				if (myJobCoordinator != null) {
					html.append("<form method='POST' action='/fhir/$pause' style='display:inline'>");
					html.append("<input type='hidden' name='jobId' value='").append(job.getInstanceId()).append("'/>");
					html.append("<button type='submit'>Pause</button></form> ");

					html.append("<form method='POST' action='/fhir/$resume' style='display:inline'>");
					html.append("<input type='hidden' name='jobId' value='").append(job.getInstanceId()).append("'/>");
					html.append("<button type='submit'>Resume</button></form> ");

					html.append("<form method='POST' action='/fhir/$cancel' style='display:inline'>");
					html.append("<input type='hidden' name='jobId' value='").append(job.getInstanceId()).append("'/>");
					html.append("<button type='submit'>Cancel</button></form>");
				}
				html.append("</td>");
				html.append("</tr>");
			}

			html.append("</table>");
			html.append("<br/><a href='/demo/create-job'>Create New Job</a> | ");
			html.append("<a href='/demo/'>Home</a>");
			html.append("</body></html>");

			return html.toString();
		} catch (Exception e) {
			return """
					<html>
					<body>
					<h2>Error Listing Jobs</h2>
					<p>%s</p>
					</body>
					</html>
					""".formatted(e.getMessage());
		}
	}

	@GetMapping("/job-status/{jobId}")
	@Transactional(readOnly = true)
	public String getJobStatus(@PathVariable String jobId) {
		try {
			Optional<Batch2JobInstanceEntity> jobOpt = myJobInstanceRepository.findByInstanceId(jobId);
			if (jobOpt.isEmpty()) {
				return "<html><body><h2>Job not found</h2><a href='/demo/list-jobs'>Back to list</a></body></html>";
			}

			Batch2JobInstanceEntity job = jobOpt.get();
			return """
					<html>
					<body>
					<h2>Job Status</h2>
					<p><strong>Job ID:</strong> %s</p>
					<p><strong>Definition:</strong> %s v%d</p>
					<p><strong>Status:</strong> %s</p>
					<p><strong>Created:</strong> %s</p>
					<p><strong>Updated:</strong> %s</p>
					<p><strong>Params:</strong> %s</p>
					<br/>
					<a href='/demo/list-jobs'>Back to list</a>
					</body>
					</html>
					""".formatted(job.getInstanceId(), job.getJobDefinitionId(), job.getJobDefinitionVersion(),
							job.getStatus(), job.getCreateTime(), job.getUpdateTime(), job.getParams());
		} catch (Exception e) {
			return """
					<html>
					<body>
					<h2>Error Getting Job Status</h2>
					<p>%s</p>
					</body>
					</html>
					""".formatted(e.getMessage());
		}
	}

	private String truncateId(String id) {
		if (id != null && id.length() > 20) {
			return id.substring(0, 20) + "...";
		}
		return id;
	}
}

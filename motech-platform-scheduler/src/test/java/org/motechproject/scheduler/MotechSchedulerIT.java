package org.motechproject.scheduler;

import org.joda.time.DateTime;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.motechproject.model.CronSchedulableJob;
import org.motechproject.model.MotechEvent;
import org.motechproject.model.RepeatingSchedulableJob;
import org.motechproject.model.RunOnceSchedulableJob;
import org.motechproject.server.event.EventListenerRegistry;
import org.motechproject.server.event.annotations.MotechListenerEventProxy;
import org.motechproject.util.DateUtil;
import org.quartz.CronTrigger;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.SchedulerException;
import org.quartz.impl.matchers.GroupMatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.quartz.JobKey.jobKey;
import static org.quartz.TriggerKey.triggerKey;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"/testPlatformSchedulerApplicationContext.xml"})
public class MotechSchedulerIT {

	public static final String SUBJECT = "testEvent";
	@Autowired
	private MotechSchedulerService motechScheduler;

	@Autowired
	private EventListenerRegistry eventListenerRegistry;

	@Autowired
	private SchedulerFactoryBean schedulerFactoryBean;

	private String uuidStr = UUID.randomUUID().toString();

	private long scheduledHour;
	private long scheduledMinute;
	private long scheduledSecond;

	private boolean executed;


	@Before
	public void setUp() {
		Calendar cal = Calendar.getInstance();
		cal.setTime(new Date());
		cal.add(1, Calendar.MINUTE);
		DateTime now = DateUtil.now();

		scheduledHour = now.getHourOfDay();
		scheduledMinute = now.getMinuteOfHour() + 1;
		scheduledSecond = now.getSecondOfMinute();
		if (scheduledMinute == 59) {
			scheduledHour = (scheduledHour + 1) % 24;
			scheduledMinute = 0;
		}
	}

	@Test
	@Ignore
	public void scheduleCronJobTest() {

		Map<String, Object> params = new HashMap<String, Object>();
		params.put("JobID", UUID.randomUUID().toString());
		String testSubject = "MotechSchedulerITScheduleTest";
		MotechEvent motechEvent = new MotechEvent(testSubject, params);

		Method handlerMethod = ReflectionUtils.findMethod(MotechSchedulerIT.class, "handleEvent", MotechEvent.class);
		eventListenerRegistry.registerListener(new MotechListenerEventProxy("handleEvent", this, handlerMethod), testSubject);

		CronSchedulableJob cronSchedulableJob = new CronSchedulableJob(motechEvent, String.format("%d %d %d * * ?", scheduledSecond, scheduledMinute, scheduledHour));
		motechScheduler.scheduleJob(cronSchedulableJob);

		//Thread.sleep(90000);   // seems to pass without sleep
		if (!executed) {
			Assert.fail("scheduler job not handled ..........");
		}
	}

	public void handleEvent(MotechEvent motechEvent) {
		System.out.println(motechEvent.getSubject());
		executed = true;
	}

	@Test
	public void scheduleTest() throws Exception {
		Map<String, Object> params = new HashMap<String, Object>();
		params.put("JobID", uuidStr);
		MotechEvent motechEvent = new MotechEvent("testEvent", params);
		CronSchedulableJob cronSchedulableJob = new CronSchedulableJob(motechEvent, "0 0 12 * * ?");

		int scheduledJobsNum = getNumberOfScheduledJobs();

		motechScheduler.scheduleJob(cronSchedulableJob);

		assertEquals(scheduledJobsNum + 1, getNumberOfScheduledJobs());
	}

	@Test
	public void scheduleExistingJobTest() throws Exception {

		Map<String, Object> params = new HashMap<String, Object>();
		params.put("JobID", uuidStr);
		MotechEvent motechEvent = new MotechEvent("testEvent", params);
		CronSchedulableJob cronSchedulableJob = new CronSchedulableJob(motechEvent, "0 0 12 * * ?");


		Map<String, Object> newParameters = new HashMap<String, Object>();
		newParameters.put("param1", "value1");
		newParameters.put("JobID", uuidStr);

		String newCronExpression = "0 0 0 * * ?";

		MotechEvent newMotechEvent = new MotechEvent("testEvent", newParameters);
		CronSchedulableJob newCronSchedulableJob = new CronSchedulableJob(newMotechEvent, newCronExpression);

		int scheduledJobsNum = getNumberOfScheduledJobs();

		motechScheduler.scheduleJob(cronSchedulableJob);
		assertEquals(scheduledJobsNum + 1, getNumberOfScheduledJobs());


		motechScheduler.scheduleJob(newCronSchedulableJob);

		assertEquals(scheduledJobsNum + 1, getNumberOfScheduledJobs());

		CronTrigger trigger = (CronTrigger) schedulerFactoryBean.getScheduler().getTrigger(triggerKey("testEvent-" + uuidStr, MotechSchedulerServiceImpl.JOB_GROUP_NAME));
		JobDetail jobDetail = schedulerFactoryBean.getScheduler().getJobDetail(jobKey("testEvent-" + uuidStr, MotechSchedulerServiceImpl.JOB_GROUP_NAME));
		JobDataMap jobDataMap = jobDetail.getJobDataMap();

		assertEquals(newCronExpression, trigger.getCronExpression());
		assertEquals(3, jobDataMap.size());
	}

	@Test(expected = MotechSchedulerException.class)
	public void scheduleInvalidCronExprTest() throws Exception {

		Map<String, Object> params = new HashMap<String, Object>();
		params.put("JobID", uuidStr);
		MotechEvent motechEvent = new MotechEvent("testEvent", params);
		CronSchedulableJob cronSchedulableJob = new CronSchedulableJob(motechEvent, " ?");

		motechScheduler.scheduleJob(cronSchedulableJob);

	}


	@Test(expected = IllegalArgumentException.class)
	public void scheduleNullJobTest() throws Exception {
		motechScheduler.scheduleJob(null);
	}

	@Test(expected = IllegalArgumentException.class)
	public void scheduleNullRunOnceJobTest() throws Exception {
		motechScheduler.scheduleRunOnceJob(null);
	}

	@Test(expected = IllegalArgumentException.class)
	public void scheduleNullRepeatingJobTest() throws Exception {
		motechScheduler.scheduleRepeatingJob(null);
	}

	@Test
	public void updateScheduledJobHappyPathTest() throws Exception {
		Map<String, Object> params = new HashMap<String, Object>();
		params.put("JobID", uuidStr);
		MotechEvent motechEvent = new MotechEvent("testEvent", params);
		CronSchedulableJob cronSchedulableJob = new CronSchedulableJob(motechEvent, "0 0 12 * * ?");

		motechScheduler.scheduleJob(cronSchedulableJob);

		String patientIdKeyName = "patientId";
		String patientId = "1";
		params = new HashMap<String, Object>();
		params.put(patientIdKeyName, patientId);
		params.put("JobID", uuidStr);

		motechEvent = new MotechEvent("testEvent", params);

		motechScheduler.updateScheduledJob(motechEvent);

		JobDataMap jobDataMap = schedulerFactoryBean.getScheduler().getJobDetail(jobKey("testEvent-" + uuidStr, MotechSchedulerServiceImpl.JOB_GROUP_NAME)).getJobDataMap();

		assertEquals(patientId, jobDataMap.getString(patientIdKeyName));
	}

	@Test(expected = IllegalArgumentException.class)
	public void updateScheduledJobNullTest() throws Exception {
		motechScheduler.updateScheduledJob(null);
	}

	@Test
	public void rescheduleJobHappyPathTest() throws Exception {
		Map<String, Object> params = new HashMap<String, Object>();
		params.put("JobID", uuidStr);
		MotechEvent motechEvent = new MotechEvent("testEvent", params);
		CronSchedulableJob cronSchedulableJob = new CronSchedulableJob(motechEvent, "0 0 12 * * ?");

		motechScheduler.scheduleJob(cronSchedulableJob);

		int scheduledJobsNum = getNumberOfScheduledJobs();

		String newCronExpression = "0 0 10 * * ?";

		motechScheduler.rescheduleJob("testEvent", uuidStr, newCronExpression);
		assertEquals(scheduledJobsNum, getNumberOfScheduledJobs());

		CronTrigger trigger = (CronTrigger) schedulerFactoryBean.getScheduler().getTrigger(triggerKey("testEvent-" + uuidStr, MotechSchedulerServiceImpl.JOB_GROUP_NAME));
		String triggerCronExpression = trigger.getCronExpression();

		assertEquals(newCronExpression, triggerCronExpression);
	}

	@Test(expected = IllegalArgumentException.class)
	public void rescheduleJobNullJobIdTest() {
		motechScheduler.rescheduleJob(null, null, "");
	}


	@Test(expected = IllegalArgumentException.class)
	public void rescheduleJobNullCronExpressionTest() {
		motechScheduler.rescheduleJob("", "", null);
	}

	@Test(expected = MotechSchedulerException.class)
	public void rescheduleJobInvalidCronExpressionTest() {

		Map<String, Object> params = new HashMap<String, Object>();
		params.put("JobID", uuidStr);
		MotechEvent motechEvent = new MotechEvent("testEvent", params);
		CronSchedulableJob cronSchedulableJob = new CronSchedulableJob(motechEvent, "0 0 12 * * ?");

		motechScheduler.scheduleJob(cronSchedulableJob);

		motechScheduler.rescheduleJob("testEvent", uuidStr, "?");
	}

	@Test(expected = MotechSchedulerException.class)
	public void rescheduleNotExistingJobTest() {
		motechScheduler.rescheduleJob("", "0", "?");
	}

	@Test
	public void scheduleRunOnceJobTest() throws Exception {

		String uuidStr = UUID.randomUUID().toString();

		Map<String, Object> params = new HashMap<String, Object>();
		params.put("JobID", uuidStr);
		MotechEvent motechEvent = new MotechEvent("TestEvent", params);
		RunOnceSchedulableJob schedulableJob = new RunOnceSchedulableJob(motechEvent, new Date((new Date()).getTime() + 5000));

		int scheduledJobsNum = getNumberOfScheduledJobs();

		motechScheduler.scheduleRunOnceJob(schedulableJob);

		assertEquals(scheduledJobsNum + 1, getNumberOfScheduledJobs());
	}


	@Test(expected = IllegalArgumentException.class)
	public void scheduleRunOncePastJobTest() throws Exception {

		Calendar calendar = Calendar.getInstance();
		calendar.add(Calendar.DATE, -1);

		MotechEvent motechEvent = new MotechEvent(null, null);

		RunOnceSchedulableJob schedulableJob = new RunOnceSchedulableJob(motechEvent, calendar.getTime());

		schedulerFactoryBean.getScheduler().getTriggerKeys(GroupMatcher.triggerGroupEquals(MotechSchedulerServiceImpl.JOB_GROUP_NAME));
		motechScheduler.scheduleRunOnceJob(schedulableJob);
	}

	@Test
	public void testScheduleRepeatingJob() throws Exception {
		String uuidStr = UUID.randomUUID().toString();

		Map<String, Object> params = new HashMap<String, Object>();
		params.put("JobID", uuidStr);
		MotechEvent motechEvent = new MotechEvent("TestEvent", params);

		Calendar cal = Calendar.getInstance();
		Date start = cal.getTime();
		cal.add(Calendar.DATE, 1);
		Date end = cal.getTime();

		RepeatingSchedulableJob schedulableJob = new RepeatingSchedulableJob(motechEvent, start, end, 5, 5000);

		int scheduledJobsNum = getNumberOfScheduledJobs();

		motechScheduler.scheduleRepeatingJob(schedulableJob);

		assertEquals(scheduledJobsNum + 1, getNumberOfScheduledJobs());
	}

	@Test(expected = IllegalArgumentException.class)
	public void testScheduleRepeatingJobTest_NoStartDate() throws Exception {
		String uuidStr = UUID.randomUUID().toString();

		Map<String, Object> params = new HashMap<String, Object>();
		params.put("JobID", uuidStr);
		MotechEvent motechEvent = new MotechEvent("TestEvent", params);
		RepeatingSchedulableJob schedulableJob = new RepeatingSchedulableJob(motechEvent, null, new Date(), 5, 5000);

		schedulerFactoryBean.getScheduler().getTriggerKeys(GroupMatcher.triggerGroupEquals(MotechSchedulerServiceImpl.JOB_GROUP_NAME));
		motechScheduler.scheduleRepeatingJob(schedulableJob);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testScheduleRepeatingJobTest_NoEndDate() throws Exception {
		String uuidStr = UUID.randomUUID().toString();

		Map<String, Object> params = new HashMap<String, Object>();
		params.put("JobID", uuidStr);
		MotechEvent motechEvent = new MotechEvent("TestEvent", params);
		RepeatingSchedulableJob schedulableJob = new RepeatingSchedulableJob(motechEvent, new Date(), null, 5, 5000);

		schedulerFactoryBean.getScheduler().getTriggerKeys(GroupMatcher.triggerGroupEquals(MotechSchedulerServiceImpl.JOB_GROUP_NAME));

		motechScheduler.scheduleRepeatingJob(schedulableJob);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testScheduleRepeatingJobTest_NullJob() throws Exception {
		RepeatingSchedulableJob schedulableJob = null;
		schedulerFactoryBean.getScheduler().getTriggerKeys(GroupMatcher.triggerGroupEquals(MotechSchedulerServiceImpl.JOB_GROUP_NAME));
		motechScheduler.scheduleRepeatingJob(schedulableJob);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testScheduleRepeatingJobTest_NullEvent() throws Exception {
		MotechEvent motechEvent = null;
		RepeatingSchedulableJob schedulableJob = new RepeatingSchedulableJob(motechEvent, new Date(), new Date(), 5, 5000);

		schedulerFactoryBean.getScheduler().getTriggerKeys(GroupMatcher.triggerGroupEquals(MotechSchedulerServiceImpl.JOB_GROUP_NAME));

		motechScheduler.scheduleRepeatingJob(schedulableJob);
	}

	@Test
	public void unscheduleJobTest() throws Exception {

		Map<String, Object> params = new HashMap<String, Object>();
		params.put("JobID", uuidStr);
		MotechEvent motechEvent = new MotechEvent(SUBJECT, params);
		CronSchedulableJob cronSchedulableJob = new CronSchedulableJob(motechEvent, "0 0 12 * * ?");

		motechScheduler.scheduleJob(cronSchedulableJob);
		int scheduledJobsNum = getNumberOfScheduledJobs();

		motechScheduler.unscheduleJob(SUBJECT, uuidStr);

		assertEquals(scheduledJobsNum - 1, getNumberOfScheduledJobs());
	}

	@Test
	public void unscheduleJobsGivenAJobIdPrefix() throws Exception {

		motechScheduler.scheduleJob(getJob("testJobId.1.1"));
		motechScheduler.scheduleJob(getJob("testJobId.1.2"));
		motechScheduler.scheduleJob(getJob("testJobId.1.3"));

		int numOfScheduledJobs = getNumberOfScheduledJobs();

		motechScheduler.unscheduleAllJobs("testJobId");

		assertEquals(numOfScheduledJobs - 3, getNumberOfScheduledJobs());
	}

	private CronSchedulableJob getJob(String jobId) {
		Map<String, Object> params = new HashMap<String, Object>();
		params.put("JobID", jobId);
		MotechEvent motechEvent = new MotechEvent("testEvent", params);
		return new CronSchedulableJob(motechEvent, "0 0 12 * * ?");
	}

	private int getNumberOfScheduledJobs() throws SchedulerException {
		return schedulerFactoryBean.getScheduler().getTriggerKeys(GroupMatcher.triggerGroupEquals(MotechSchedulerServiceImpl.JOB_GROUP_NAME)).size();
	}
}

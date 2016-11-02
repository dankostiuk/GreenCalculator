package com.quartz;

import static org.quartz.JobBuilder.newJob;
import static org.quartz.TriggerBuilder.newTrigger;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import org.quartz.CronScheduleBuilder;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.impl.StdSchedulerFactory;

/**
 * Sets up FetchDataJob and configures quartz fire time.
 * 
 * @author dan
 */
public class QuartzListener implements ServletContextListener {

	Scheduler _scheduler = null;
	
	@Override
	public void contextDestroyed(ServletContextEvent servletContext) {
		System.out.println("Context Destroyed");
        try 
        {
                _scheduler.shutdown();
        } 
        catch (SchedulerException e) 
        {
                e.printStackTrace();
        }
	}

	@Override
	public void contextInitialized(ServletContextEvent servletContext) {
		System.out.println("Context Initialized");
		
		 try {
             // Setup the Job class and the Job group
             JobDetail job = newJob(FetchDataJob.class).withIdentity(
                             "CronQuartzJob", "Group").build();
            
             // every 5 minutes
             Trigger trigger = newTrigger()
				.withIdentity("TriggerName", "Group")
				.withSchedule(SimpleScheduleBuilder.simpleSchedule().withIntervalInMinutes(5)
						.repeatForever())
				.build();
 				
             // Setup the Job and Trigger with Scheduler & schedule jobs
             _scheduler = new StdSchedulerFactory().getScheduler();
             _scheduler.start();
             _scheduler.scheduleJob(job, trigger);
	     }
	     catch (SchedulerException e) {
	             e.printStackTrace();
	     }
		
	}

}

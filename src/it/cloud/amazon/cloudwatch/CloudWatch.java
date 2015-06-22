package it.cloud.amazon.cloudwatch;

import it.cloud.amazon.Configuration;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClient;
import com.amazonaws.services.cloudwatch.model.Datapoint;
import com.amazonaws.services.cloudwatch.model.Dimension;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsRequest;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsResult;
import com.amazonaws.services.cloudwatch.model.ListMetricsRequest;
import com.amazonaws.services.cloudwatch.model.ListMetricsResult;
import com.amazonaws.services.cloudwatch.model.Metric;
import com.amazonaws.services.cloudwatch.model.StandardUnit;
import com.amazonaws.services.cloudwatch.model.Statistic;

public class CloudWatch {
	
	@SuppressWarnings("unused")
	private static final Logger logger = LoggerFactory.getLogger(CloudWatch.class);
	
	private static com.amazonaws.services.cloudwatch.AmazonCloudWatchClient client = null;
	
	public static com.amazonaws.services.cloudwatch.AmazonCloudWatchClient connect() {
		if (client == null) {
			Region r = Region.getRegion(Regions.fromName(Configuration.REGION));

			client = new AmazonCloudWatchClient(Configuration.AWS_CREDENTIALS);
			client.setRegion(r);
		}
		return client;
	}
	
	public static final int DEFAULT_PERIOD = 60 * 5;
	public static Date getStartTime(int hoursBefore, int minsBefore, int secsBefore) {
		if (hoursBefore < 0)
			hoursBefore = 0;
		if (minsBefore < 0)
			minsBefore = 0;
		if (secsBefore < 0)
			secsBefore = 0;
		
		GregorianCalendar calendar = new GregorianCalendar();
		calendar.set(GregorianCalendar.HOUR_OF_DAY, calendar.get(GregorianCalendar.HOUR_OF_DAY) - hoursBefore);
		calendar.set(GregorianCalendar.MINUTE, calendar.get(GregorianCalendar.MINUTE) - minsBefore);
		calendar.set(GregorianCalendar.SECOND, calendar.get(GregorianCalendar.SECOND) - secsBefore);
		
		return calendar.getTime();
	}
	public static Date oneHourAgo() {
		return getStartTime(1, 0, 0);
	}
	public static Date twoHoursAgo() {
		return getStartTime(2, 0, 0);
	}
	public static Date fourHoursAgo() {
		return getStartTime(4, 0, 0);
	}
	
	public static List<Metric> listMetrics(String filter) {
		connect();
		
		ListMetricsRequest req = new ListMetricsRequest();
		if (filter != null)
			req.setMetricName(filter);
		
		ListMetricsResult res = client.listMetrics(req);
		return res.getMetrics();
	}
	
	public static Dimension getInstanceDimension(String instanceId) {
		if (instanceId == null)
			return null;
		
		Dimension dim = new Dimension();
		dim.setName("InstanceId");
		dim.setValue(instanceId);
		return dim;
	}
	
	public static List<Datapoint> getInstanceMetric(String metricName, String instanceId, Date startTime, int period, Statistic statistic, StandardUnit unit) {
		return getMetric(metricName, getInstanceDimension(instanceId), "AWS/EC2", startTime, period, statistic, unit);
	}
	
	public static List<Datapoint> getMetric(String metricName, Dimension dim, String namespace, Date startTime, int period, Statistic statistic, StandardUnit unit) {
		connect();
		
		GetMetricStatisticsRequest req = new GetMetricStatisticsRequest();
		
		req.setMetricName(metricName);
		
		List<Dimension> dimensions = new ArrayList<Dimension>();
		dimensions.add(dim);
		req.setDimensions(dimensions);
		
		req.setNamespace(namespace);
		
		req.setStartTime(startTime);
		req.setEndTime(new Date());
		
		if (period % 60 != 0)
			period = (period / 60) * 60;
		req.setPeriod(period);
		
		if (unit != null)
			req.setUnit(unit);
		
		if (statistic == null)
			statistic = Statistic.Average;
		List<String> statistics = new ArrayList<String>();
		statistics.add(statistic.name());
		req.setStatistics(statistics);
		
		GetMetricStatisticsResult res = client.getMetricStatistics(req);
		return res.getDatapoints();
	}
	
	public static void writeInstanceMetricToFile(Path file, String metricName, String instanceId, Date startTime, int period, Statistic statistic, StandardUnit unit) throws Exception {
		writeMetricToFile(file, metricName, getInstanceDimension(instanceId), "AWS/EC2", startTime, period, statistic, unit);
	}
	
	public static void writeMetricToFile(Path file, String metricName, Dimension dim, String namespace, Date startTime, int period, Statistic statistic, StandardUnit unit) throws Exception {
		List<Datapoint> datapoints = getMetric(metricName, dim, namespace, startTime, period, statistic, unit);
		try (PrintWriter out = new PrintWriter(file.toFile())) {
			out.println("Timestamp,Metric,Statistic,Value,Unit");
			for (Datapoint d : datapoints) {
				double value = 0;
				switch (statistic) {
				case Sum:
					value = d.getSum();
					break;
				case Maximum:
					value = d.getMaximum();
					break;
				case Minimum:
					value = d.getMinimum();
					break;
				case SampleCount:
					value = d.getSampleCount();
					break;
				default:
					value = d.getAverage();
				}
				out.println(d.getTimestamp().getTime() + "," + metricName + "," + statistic.name() + "," + doubleFormatter.format(value) + "," + d.getUnit());
			}
			
			out.flush();
		}
	}
	
	private static DecimalFormat doubleFormatter() {
		DecimalFormatSymbols otherSymbols = new DecimalFormatSymbols(Locale.getDefault());
		otherSymbols.setDecimalSeparator('.');
		DecimalFormat myFormatter = new DecimalFormat("0.000", otherSymbols);
		return myFormatter;
	}
	private static DecimalFormat doubleFormatter = doubleFormatter();

}

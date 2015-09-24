package it.cloud.amazon.cloudwatch;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.services.cloudwatch.model.Datapoint;
import com.amazonaws.services.cloudwatch.model.Statistic;

public class Datum implements Comparable<Datum> {
	
	private static final Logger logger = LoggerFactory.getLogger(Datum.class);
	
	public long timestamp;
	public String metric;
	public String statistic;
	public double value;
	public String unit;
	
	public Datum(long timestamp, String metric, String statistic, double value, String unit) {
		this.timestamp = timestamp;
		this.metric = metric;
		this.statistic = statistic;
		this.value = value;
		this.unit = unit;
	}
	
	public Datum(String csv) {
		String[] splitted = csv.split(",");
		if (splitted.length != 5)
			throw new RuntimeException("Line (" + csv + ") not valid!");
		
		this.timestamp = Long.valueOf(splitted[0]);
		this.metric = splitted[1];
		this.statistic = splitted[2];
		this.value = Double.valueOf(splitted[3]);
		this.unit = splitted[4];
	}
	
	public Datum(Datapoint d, String metric, Statistic statistic) {
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
		
		timestamp = d.getTimestamp().getTime();
		this.metric = metric;
		this.statistic = statistic.name();
		this.unit = d.getUnit();
	}
	
	@Override
	public String toString() {
		return String.format(
				"%s[timestamp: %d, metric: %s, statistic: %s, value: %f, unit: %s]",
				this.getClass().getName(), timestamp, metric, statistic, value, unit);
	}
	
	public static String getCSVHeader() {
		return "Timestamp,Metric,Statistic,Value,Unit";
	}
	
	public String toCSV() {
		return String.format(
				"%d,%s,%s,%s,%s",
				timestamp, metric, statistic, doubleFormatter.format(value), unit);
	}

	@Override
	public int compareTo(Datum o) {
		if (timestamp == o.timestamp && metric.equals(o.metric) && statistic.equals(o.statistic) && value == o.value && unit.equals(o.unit))
			return 0;
		if (metric.equals(o.metric) && statistic.equals(o.statistic) && unit.equals(o.unit)) {
			if (timestamp <= o.timestamp)
				return -1;
			else
				return +1;
		}
		logger.error("Comparing two incomparable elements!");
		return Integer.MIN_VALUE;
	}
	
	private static DecimalFormat doubleFormatter() {
		DecimalFormatSymbols otherSymbols = new DecimalFormatSymbols(Locale.getDefault());
		otherSymbols.setDecimalSeparator('.');
		DecimalFormat myFormatter = new DecimalFormat("0.000", otherSymbols);
		return myFormatter;
	}
	private static DecimalFormat doubleFormatter = doubleFormatter();
	
	public static List<Datum> getAllData(Path p) throws Exception {
		if (p == null || !p.toFile().exists())
			throw new RuntimeException("File null or not found.");
		
		ArrayList<Datum> data = new ArrayList<Datum>();
		try (Scanner sc = new Scanner(p)) {
			while (sc.hasNextLine()) {
				String line = sc.nextLine();
				try {
					data.add(new Datum(line));
				} catch (Exception e) { }
			}
		}
		return data;
	}
	
	public static void writeAllData(Path p, List<Datum> data) throws Exception {
		try (PrintWriter out = new PrintWriter(p.toFile())) {
			out.println(Datum.getCSVHeader());
			for (Datum d : data)
				out.println(d.toCSV());
			
			out.flush();
		}
	}
	
	public static List<Datum> getAllData(List<Datapoint> datapoints, String metric, Statistic statistic) {
		ArrayList<Datum> data = new ArrayList<Datum>();
		for (Datapoint d : datapoints)
			data.add(new Datum(d, metric, statistic));
		Collections.sort(data);
		return data;
	}
	
}
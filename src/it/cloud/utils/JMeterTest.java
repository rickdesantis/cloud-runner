package it.cloud.utils;

import it.cloud.CloudService;
import it.cloud.Instance;
import it.cloud.VirtualMachine;
import it.cloud.amazon.ec2.Configuration;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class JMeterTest {
	
	private static final Logger logger = LoggerFactory.getLogger(JMeterTest.class);
	
	private static DecimalFormat nameFormatter(int chars) {
		if (chars <= 0)
			throw new RuntimeException("You need at least one char.");
		
		String pattern = "0";
	    for (int i = 0; i < chars; ++i)
	    	pattern += "0";
		
		DecimalFormat myFormatter = new DecimalFormat(pattern);
		return myFormatter;
	}
	
	private static DecimalFormat nameFormatter = nameFormatter(10);
	
	private static void considerDataFile(Document doc, Path data, int clients) throws IOException {
		final String ultimateThreadGroup = "kg.apc.jmeter.threads.UltimateThreadGroup";
		final String variableThroughputTimer = "kg.apc.jmeter.timers.VariableThroughputTimer";
		
		if (doc.getElementsByTagName(ultimateThreadGroup).getLength() != 1)
			throw new RuntimeException("No " + ultimateThreadGroup + " found or too many of them in the same file.");
		
		if (clients < 1)
			throw new RuntimeException("There should be at least one client running the JMX.");
		
		boolean useVariableTimer = (doc.getElementsByTagName(variableThroughputTimer).getLength() == 1);
		
		Node variableTimer = null;
		Element variableTimerCol = null;
		if (useVariableTimer) {
			variableTimer= doc.getElementsByTagName(variableThroughputTimer).item(0);
			
			while (variableTimer.hasChildNodes())
				variableTimer.removeChild(variableTimer.getFirstChild());
			variableTimer.appendChild(doc.createTextNode("\n"));
			variableTimerCol = doc.createElement("collectionProp");
			variableTimerCol.appendChild(doc.createTextNode("\n"));
			variableTimerCol.setAttribute("name", "load_profile");
			variableTimer.appendChild(variableTimerCol);
		}
		
		Node ultimateGroup = doc.getElementsByTagName(ultimateThreadGroup).item(0);
		
//		while (ultimateGroup.hasChildNodes())
//			ultimateGroup.removeChild(ultimateGroup.getFirstChild());
		ultimateGroup.appendChild(doc.createTextNode("\n"));
		Element ultimateGroupCol = doc.createElement("collectionProp");
		ultimateGroupCol.appendChild(doc.createTextNode("\n"));
		ultimateGroupCol.setAttribute("name", "ultimatethreadgroupdata");
//		ultimateGroup.appendChild(ultimateGroupCol);
		
		NodeList collectionProps = ((Element)ultimateGroup).getElementsByTagName("collectionProp");
		if (collectionProps.getLength() > 0) {
			boolean done = false;
			for (int i = 0; i < collectionProps.getLength() && !done; ++i)
				if (((Element)collectionProps.item(i)).getAttribute("name").equalsIgnoreCase("ultimatethreadgroupdata")) {
					ultimateGroup.removeChild(collectionProps.item(i));
					done = true;
				}
		}
		ultimateGroup.appendChild(ultimateGroupCol);

		int start_time = 0;
		int index = 1;
		
		try (Scanner input = new Scanner(data)) {
			while (input.hasNextLine()) {
				String nameProp = nameFormatter.format(index);
				String line = input.nextLine();
				String[] values = line.trim().split(" ");
				if (values.length < 2)
					continue;
				
				if (useVariableTimer) {
					Element colp = doc.createElement("collectionProp");
					colp.appendChild(doc.createTextNode("\n"));
					colp.setAttribute("name", nameProp);
					variableTimerCol.appendChild(colp);
					
					Element prop1 = doc.createElement("stringProp");
					prop1.appendChild(doc.createTextNode(values[2]));
					prop1.setAttribute("name", "1");
					Element prop2 = doc.createElement("stringProp");
					prop2.appendChild(doc.createTextNode(values[2]));
					prop2.setAttribute("name", "2");
					Element prop3 = doc.createElement("stringProp");
					prop3.appendChild(doc.createTextNode(values[1]));
					prop3.setAttribute("name", "3");
	
					colp.appendChild(prop1);
					colp.appendChild(doc.createTextNode("\n"));
					colp.appendChild(prop2);
					colp.appendChild(doc.createTextNode("\n"));
					colp.appendChild(prop3);
					colp.appendChild(doc.createTextNode("\n"));
				}
				
				{
					Element colp1 = doc.createElement("collectionProp");
					colp1.appendChild(doc.createTextNode("\n"));
					colp1.setAttribute("name", nameProp);
					ultimateGroupCol.appendChild(colp1);
	
					Element prop1 = doc.createElement("stringProp");
					prop1.appendChild(doc.createTextNode(Integer.valueOf((int)Math.ceil(Double.parseDouble(values[0]) / clients)).toString()));
					prop1.setAttribute("name", "1");
					Element prop2 = doc.createElement("stringProp");
					String st = String.valueOf(start_time);
					prop2.appendChild(doc.createTextNode(st));
					start_time = start_time + (int) Double.parseDouble(values[1]) + 20;
					prop2.setAttribute("name", "2");
					Element prop3 = doc.createElement("stringProp");
					prop3.appendChild(doc.createTextNode("10"));
					prop3.setAttribute("name", "3");
					Element prop4 = doc.createElement("stringProp");
					prop4.appendChild(doc.createTextNode(values[1]));
					prop4.setAttribute("name", "4");
					Element prop5 = doc.createElement("stringProp");
					prop5.appendChild(doc.createTextNode("10"));
					prop5.setAttribute("name", "5");
	
					colp1.appendChild(prop1);
					colp1.appendChild(doc.createTextNode("\n"));
					colp1.appendChild(prop2);
					colp1.appendChild(doc.createTextNode("\n"));
					colp1.appendChild(prop3);
					colp1.appendChild(doc.createTextNode("\n"));
					colp1.appendChild(prop4);
					colp1.appendChild(doc.createTextNode("\n"));
					colp1.appendChild(prop5);
					colp1.appendChild(doc.createTextNode("\n"));
				}

				index++;

			}
		}
	}
	
	public static class RunInstance {
		public File jmx = null;
		public List<String> fileToBeSent = new ArrayList<String>();
		public List<String> fileToBeGet = new ArrayList<String>();
	}

	public static RunInstance createModifiedFile(Path baseJmx, String localPathTest, String remotePathTest, String dataFile, int clients, Object... substitutions) {
		RunInstance res = new RunInstance();
		
//		Date date = new Date();
//		String startTime = String.valueOf(date.getTime());
		String testName = "test";
		String log_table = String.format("%s/test_table.jtl", remotePathTest);
		String log_tree = String.format("%s/test_tree.jtl", remotePathTest);
		String log_aggregate = String.format("%s/test_aggregate.jtl", remotePathTest);
		String log_graph = String.format("%s/test_graph.jtl", remotePathTest);
		String log_tps = String.format("%s/test_tps.jtl", remotePathTest);
		
		res.fileToBeGet.add("test_table.jtl");
		res.fileToBeGet.add("test_tree.jtl");
		res.fileToBeGet.add("test_aggregate.jtl");
		res.fileToBeGet.add("test_graph.jtl");
		res.fileToBeGet.add("test_tps.jtl");

		try {
			File fXmlFile = Configuration.getPathToFile(baseJmx.toString()).toFile();
			DocumentBuilderFactory dbFactory = DocumentBuilderFactory
					.newInstance();
			DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
			Document doc = dBuilder.parse(fXmlFile);
			doc.getDocumentElement().normalize();
			
			considerDataFile(doc, Configuration.getPathToFile(dataFile), clients);

			try {
				XPath xpath = XPathFactory.newInstance().newXPath();
				String expression = "//ResultCollector[@guiclass='TableVisualizer']/stringProp";
				Node xpathNode = (Node) xpath.evaluate(expression, doc,
						XPathConstants.NODE);
				xpathNode.setTextContent(log_table);
				expression = "//ResultCollector[@guiclass='ViewResultsFullVisualizer']/stringProp";
				xpathNode = (Node) xpath.evaluate(expression, doc,
						XPathConstants.NODE);
				xpathNode.setTextContent(log_tree);
				expression = "//ResultCollector[@guiclass='StatGraphVisualizer']/stringProp";
				xpathNode = (Node) xpath.evaluate(expression, doc,
						XPathConstants.NODE);
				xpathNode.setTextContent(log_aggregate);
				expression = "//ResultCollector[@guiclass='GraphVisualizer']/stringProp";
				xpathNode = (Node) xpath.evaluate(expression, doc,
						XPathConstants.NODE);
				xpathNode.setTextContent(log_graph);
				expression = "//kg.apc.jmeter.vizualizers.CorrectedResultCollector/stringProp[@name='filename']";
				xpathNode = (Node) xpath.evaluate(expression, doc,
						XPathConstants.NODE);
				xpathNode.setTextContent(log_tps);

//				expression = "//elementProp[@elementType='HTTPArgument' and @name='testname']/stringProp[@name='Argument.value']";
//				xpathNode = (Node) xpath.evaluate(expression, doc,
//						XPathConstants.NODE);
//				xpathNode.setTextContent(testName);
//				expression = "//elementProp[@elementType='HTTPArgument' and @name='starttime']/stringProp[@name='Argument.value']";
//				xpathNode = (Node) xpath.evaluate(expression, doc,
//						XPathConstants.NODE);
//				xpathNode.setTextContent(startTime);

			} catch (XPathExpressionException e) {
				logger.error("Error while setting the paths to the results.", e);
			}

			File jmxFile = Paths.get(localPathTest, testName + ".jmx").toFile();
			TransformerFactory transformerFactory = TransformerFactory
					.newInstance();
			Transformer transformer = transformerFactory.newTransformer();
			DOMSource source = new DOMSource(doc);
			doc.setXmlStandalone(true);
			
			try {
				jmxFile.getParentFile().mkdirs();
			} catch (Exception e) { }
			
			if (substitutions.length > 0) {
				StringWriter sw = new StringWriter();
				StreamResult result = new StreamResult(sw);
				transformer.transform(source, result);
				
				sw.flush();
				
				try (PrintWriter out = new PrintWriter(new FileWriter(jmxFile))) {
					out.printf(sw.toString(), substitutions);
					out.flush();
				}
			} else {
				StreamResult result = new StreamResult(jmxFile);
				transformer.transform(source, result);
			}
			
			res.jmx = jmxFile;
			
			return res;
		} catch (Exception e) {
			logger.error("Error while creating or exporting a modified JMX file.", e);
		}
		
		return null;
	}
	
	private String clientImageId;
	private int clients;
	private String localPath;
	private String remotePath;
	private String jmeterPath;
	private String data;
	private Object[] substitutions;
	
	public JMeterTest(String clientImageId, int clients, String localPath, String remotePath, String jmeterPath, String data, Object... substitutions) {
		this.clientImageId = clientImageId;
		this.clients = clients;
		this.localPath = localPath;
		this.remotePath = remotePath;
		this.jmeterPath = jmeterPath;
		this.data = data;
		this.substitutions = substitutions;
		
		if (clients <= 0)
			throw new RuntimeException("You need to use at least 1 client!");
		
		if (clientImageId == null || localPath == null || remotePath == null || jmeterPath == null || data == null)
			throw new RuntimeException("All the parameters are important and they cannot be null.");
	}
	
	public RunInstance createModifiedFile(Path baseJmx) throws Exception {
		return createModifiedFile(baseJmx, localPath, remotePath, data, clients, substitutions);
	}
	
	public List<Instance> startInstancesForTest(CloudService service, RunInstance run) throws Exception {
		return startInstancesForTest(service, clientImageId, clients);
	}
	
	public void performTest(VirtualMachine vm, RunInstance run) throws Exception {
		performTest(vm, run, clients, localPath, remotePath, jmeterPath);
	}
	
	public static List<Instance> startInstancesForTest(CloudService service, String clientImageId, int clients) throws Exception {
		if (clients <= 0)
			throw new RuntimeException("You need to use at least 1 client!");
		
		if (clientImageId == null)
			throw new RuntimeException("The image id cannot be null!");
		
		List<Instance> runningInstances = service.getRunningMachinesByImageId(clientImageId);
		if (runningInstances.size() < clients)
			runningInstances.addAll(service.startMachines(clients - runningInstances.size(), clientImageId));
		else {
			while (runningInstances.size() > clients)
				runningInstances.remove(runningInstances.size() - 1);
		}
		
		return runningInstances;
		
	}
	
	public static String javaParameters = null;
	
	public static void performTest(VirtualMachine vm, RunInstance run, int clients, String localPath, String remotePath, String jmeterPath) throws Exception {
		List<? extends Instance> runningInstances = vm.getInstances();
		
		if (runningInstances.size() != clients)
			throw new RuntimeException("You aren't using the exact number of running instances! (" + runningInstances.size() + " vs " + clients + ")");
		
		if (localPath == null || remotePath == null || jmeterPath == null)
			throw new RuntimeException("All the parameters are important and they cannot be null.");
		
		for (Instance i : runningInstances) {
			Ssh.exec(i, String.format("mkdir -p %s", remotePath));
			Ssh.sendFile(i, run.jmx.toString(), Paths.get(remotePath, run.jmx.getName()).toString());
			for (String s : run.fileToBeSent)
				Ssh.sendFile(i, Paths.get(localPath, s).toString(), Paths.get(remotePath, s).toString());
		}
		
		ArrayList<Thread> threads = new ArrayList<Thread>();
		
		for (Instance i : runningInstances)
			threads.add(Ssh.execInBackground(i, String.format("%s/bin/jmeter -n -t %s/%s", javaParameters != null ? "JVM_ARGS=\"" + javaParameters + "\" " + jmeterPath : jmeterPath, remotePath, run.jmx.getName())));
		
		for (Thread t : threads)
			t.join();
		
		String[] filesToBeGet = new String[run.fileToBeGet.size()];
		for (int i = 0; i < filesToBeGet.length; ++i)
			filesToBeGet[i] = run.fileToBeGet.get(i);
		
		vm.retrieveFiles(filesToBeGet, localPath, remotePath);
		
	}

}

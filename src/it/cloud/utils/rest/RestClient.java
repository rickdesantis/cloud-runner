package it.cloud.utils.rest;

import it.cloud.Configuration;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Random;
import java.util.UUID;

import org.apache.commons.io.FileUtils;
import org.restlet.Client;
import org.restlet.Context;
import org.restlet.data.Method;
import org.restlet.data.Protocol;
import org.restlet.data.Status;
import org.restlet.representation.Representation;
import org.restlet.resource.ClientResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RestClient {

	protected int port;

	private static final Logger logger = LoggerFactory
			.getLogger(RestClient.class);

	private static final Random r = new Random(UUID.randomUUID()
			.getMostSignificantBits());

	protected String ip;

	public RestClient(String ip, int port) {
		this.port = port;
		this.ip = ip;
	}

	public Status sendMessage(String path, Method method) throws Exception {
		return sendMessage(path, "", method);
	}

	public Status sendMessage(String path, String body, Method method)
			throws Exception {
		Client client = new Client(new Context(), Protocol.HTTP);

		ClientResource request = new ClientResource("http://" + ip + ":" + port
				+ path);

		Representation representation = null;
		Status status = null;

		if (method == Method.POST)
			representation = request.post(body);
		else if (method == Method.PUT)
			representation = request.put(body);
		else if (method == Method.DELETE)
			representation = request.delete();

		if (representation != null) {
			logger.info("Message sent!");
			try {
				logger.debug("Answer:\n{}", representation.getText());
			} catch (IOException e) {
				throw new Exception("Error while getting the answer.", e);
			}

			status = request.getStatus();
		} else {
			throw new Exception("Method not recognized.");
		}

		try {
			client.stop();
		} catch (Exception e) {
			throw new Exception("Error while stopping the REST client!", e);
		}

		return status;
	}

	public String getAnswerForMessage(String path, String body, Method method)
			throws Exception {
		Client client = new Client(new Context(), Protocol.HTTP);

		ClientResource request = new ClientResource("http://" + ip + ":" + port
				+ path);

		Representation representation = null;
		String answer = null;

		if (method == Method.POST)
			representation = request.post(body);
		else if (method == Method.PUT)
			representation = request.put(body);
		else if (method == Method.DELETE)
			representation = request.delete();

		if (representation != null) {
			logger.info("Message sent!");
			try {
				answer = representation.getText();
				logger.debug("Answer:\n{}", answer);
			} catch (IOException e) {
				throw new Exception("Error while getting the answer.", e);
			}
		} else {
			throw new Exception("Method not recognized.");
		}

		try {
			client.stop();
		} catch (Exception e) {
			throw new Exception("Error while stopping the REST client!", e);
		}

		return answer;
	}

	public Status sendMessageFromFile(String path, String filePath,
			Method method, Object... substitutions) throws Exception {
		String body = FileUtils.readFileToString(Configuration.getPathToFile(filePath).toFile());

		if (body == null || body.length() == 0)
			throw new Exception("The file read is empty.");

		if (substitutions.length > 0)
			body = String.format(body, substitutions);

		return sendMessage(path, body, method);

	}

	public void randomMessage() {
		ArrayList<java.lang.reflect.Method> methods = new ArrayList<java.lang.reflect.Method>();

		for (java.lang.reflect.Method method : this.getClass()
				.getDeclaredMethods()) {
			if (method.isAnnotationPresent(Message.class))
				methods.add(method);
		}

		if (methods.size() == 0) {
			logger.info("No messages found!");
			return;
		}

		int rnd = r.nextInt(methods.size());

		java.lang.reflect.Method method = methods.get(rnd);

		try {
			method.invoke(this);
		} catch (Throwable e) {
			logger.error("Error while executing the method!", e);
		}

	}

}


/*
 * Copyright (c) 2017 Dell Inc., or its subsidiaries. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 */
package io.pravega.example.gettingstarted;

import java.net.URI;
import java.util.UUID;

import io.pravega.client.ClientConfig;
import io.pravega.client.stream.Stream;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import io.pravega.client.EventStreamClientFactory;
import io.pravega.client.admin.ReaderGroupManager;
import io.pravega.client.admin.StreamManager;
import io.pravega.client.stream.EventRead;
import io.pravega.client.stream.EventStreamReader;
import io.pravega.client.stream.ReaderConfig;
import io.pravega.client.stream.ReaderGroupConfig;
import io.pravega.client.stream.ReinitializationRequiredException;
import io.pravega.client.stream.ScalingPolicy;
import io.pravega.client.stream.StreamConfiguration;
import io.pravega.client.stream.impl.JavaSerializer;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.*;
import java.io.OutputStream ;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * A simple example app that uses a Pravega Reader to read from a given scope and stream.
 */
public class HelloWorldReader {
    private static final int READER_TIMEOUT_MS = 20000;

    public final String scope;
    public final String streamName;
    public final URI controllerURI;

    public HelloWorldReader(String scope, String streamName, URI controllerURI) {
        this.scope = scope;
        this.streamName = streamName;
        this.controllerURI = controllerURI;
    }

    public String doPost(String URL,String jsonStr){
        OutputStreamWriter out = null;
        BufferedReader in = null;
        StringBuilder result = new StringBuilder();
        HttpURLConnection conn = null;
        try{
            URL url = new URL(URL);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            //发送POST请求必须设置为true
            conn.setDoOutput(true);
            conn.setDoInput(true);
            //设置连接超时时间和读取超时时间
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(10000);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");
            //获取输出流
            out = new OutputStreamWriter(conn.getOutputStream());
            out.write(jsonStr);
            out.flush();
            out.close();
            //取得输入流，并使用Reader读取
            if (200 == conn.getResponseCode()){
                in = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                String line;
                while ((line = in.readLine()) != null){
                    result.append(line);
                    System.out.println(line);
                }
            }else{
                System.out.println("ResponseCode is an error code:" + conn.getResponseCode());
            }
        }catch (Exception e){
            e.printStackTrace();
        }finally {
            try{
                if(out != null){
                    out.close();
                }
                if(in != null){
                    in.close();
                }
            }catch (IOException ioe){
                ioe.printStackTrace();
            }
        }
        return result.toString();
    }

    public void run() {
        StreamManager streamManager = StreamManager.create(controllerURI);

        final boolean scopeIsNew = streamManager.createScope(scope);
        StreamConfiguration streamConfig = StreamConfiguration.builder()
                .scalingPolicy(ScalingPolicy.fixed(1))
                .build();
        final boolean streamIsNew = streamManager.createStream(scope, streamName, streamConfig);

        final String readerGroup = UUID.randomUUID().toString().replace("-", "");
        final ReaderGroupConfig readerGroupConfig = ReaderGroupConfig.builder()
                .stream(Stream.of(scope, streamName))
                .build();
        try (ReaderGroupManager readerGroupManager = ReaderGroupManager.withScope(scope, controllerURI)) {
            readerGroupManager.createReaderGroup(readerGroup, readerGroupConfig);
        }

        try (EventStreamClientFactory clientFactory = EventStreamClientFactory.withScope(scope,
                ClientConfig.builder().controllerURI(controllerURI).build());
             EventStreamReader<String> reader = clientFactory.createReader("reader",
                                                                           readerGroup,
                                                                           new JavaSerializer<String>(),
                                                                           ReaderConfig.builder().build())) {
            System.out.format("Reading all the events from %s/%s%n", scope, streamName);
            EventRead<String> event = null;
            String lastval = "0";
            while(true){
                    do {
                        try {
                            event = reader.readNextEvent(READER_TIMEOUT_MS);
                            if (event.getEvent() != null) {
                                System.out.format("Read event '%s'%n", event.getEvent());
                                lastval = event.getEvent();

                                doPost("http://localhost:8889/delltest",lastval);                                
                            }
                        } catch (ReinitializationRequiredException e) {
                            //There are certain circumstances where the reader needs to be reinitialized
                            e.printStackTrace();
                        }
                    } while (event.getEvent() != null);
//                  System.out.format("%s",lastval);
//                  System.out.format("No more events from %s/%s%n", scope, streamName);
            }
        }
    }

    public static void main(String[] args) {
        Options options = getOptions();
        CommandLine cmd = null;
        try {
            cmd = parseCommandLineArgs(options, args);
        } catch (ParseException e) {
            System.out.format("%s.%n", e.getMessage());
            final HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("HelloWorldReader", options);
            System.exit(1);
        }

        final String scope = cmd.getOptionValue("scope") == null ? Constants.DEFAULT_SCOPE : cmd.getOptionValue("scope");
        final String streamName = cmd.getOptionValue("name") == null ? Constants.DEFAULT_STREAM_NAME : cmd.getOptionValue("name");
        final String uriString = cmd.getOptionValue("uri") == null ? Constants.DEFAULT_CONTROLLER_URI : cmd.getOptionValue("uri");
        final URI controllerURI = URI.create(uriString);

        HelloWorldReader hwr = new HelloWorldReader(scope, streamName, controllerURI);
        hwr.run();
    }

    private static Options getOptions() {
        final Options options = new Options();
        options.addOption("s", "scope", true, "The scope name of the stream to read from.");
        options.addOption("n", "name", true, "The name of the stream to read from.");
        options.addOption("u", "uri", true, "The URI to the controller in the form tcp://host:port");
        return options;
    }

    private static CommandLine parseCommandLineArgs(Options options, String[] args) throws ParseException {
        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(options, args);
        return cmd;
    }
}

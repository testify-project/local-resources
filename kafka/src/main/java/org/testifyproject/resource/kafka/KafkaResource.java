/*
 * Copyright 2016-2017 Testify Project.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.testifyproject.resource.kafka;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.apache.curator.test.TestingServer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.common.network.ListenerName;
import org.apache.kafka.common.protocol.SecurityProtocol;
import org.apache.kafka.common.utils.Time;
import org.testifyproject.LocalResourceInstance;
import org.testifyproject.LocalResourceProvider;
import org.testifyproject.TestContext;
import org.testifyproject.annotation.LocalResource;
import org.testifyproject.core.LocalResourceInstanceBuilder;
import org.testifyproject.core.util.FileSystemUtil;
import org.testifyproject.trait.PropertiesReader;

import kafka.metrics.KafkaMetricsReporter;
import kafka.server.KafkaConfig;
import kafka.server.KafkaServer;
import scala.Option;
import scala.collection.Seq;
import scala.collection.mutable.ArraySeq;

/**
 * An implementation of LocalResourceProvider that provides a local ZooKeeper test server and
 * client using Apache Curator.
 *
 * @author saden
 */
public class KafkaResource implements
        LocalResourceProvider<Map<String, String>, KafkaServer, KafkaProducer> {

    private final FileSystemUtil fileSystemUtil = FileSystemUtil.INSTANCE;
    private KafkaServer server;
    private KafkaProducer<Object, Object> client;
    private TestingServer zkServer;

    @Override
    public Map<String, String> configure(TestContext testContext,
            LocalResource localResource, PropertiesReader configReader) {
        String testName = testContext.getName();
        String logDir = fileSystemUtil.createPath("target", "kafka", testName);

        Map<String, String> brokerConfig = new HashMap<>();
        brokerConfig.put("broker.id", "0");
        brokerConfig.put("zookeeper.connect", "localhost:0");
        brokerConfig.put("zookeeper.session.timeout.ms", "10000");
        brokerConfig.put("zookeeper.connection.timeout.ms", "10000");
        brokerConfig.put("group.min.session.timeout.ms", "10000");
        brokerConfig.put("log.dir", logDir);
        brokerConfig.put("port", "0");

        return brokerConfig;
    }

    @Override
    public LocalResourceInstance<KafkaServer, KafkaProducer> start(TestContext testContext,
            LocalResource localResource,
            Map<String, String> config)
            throws Exception {
        String testName = testContext.getName();
        //create, configure, and start a zookeeper resource
        String zkTempDirectory = fileSystemUtil.createPath("target", "zookeeper", testName);
        File zkDirectory = fileSystemUtil.recreateDirectory(zkTempDirectory);
        zkServer = new TestingServer(-1, zkDirectory, true);

        config.put("zookeeper.connect", zkServer.getConnectString());
        String logDir = config.get("log.dir");
        File logDirectory = fileSystemUtil.recreateDirectory(logDir);
        config.put("log.dir", logDirectory.getAbsolutePath());

        KafkaConfig kafkaConfig = new KafkaConfig(config);
        Option<String> threadNamePrefix = Option.apply(null);
        Seq<KafkaMetricsReporter> metrics = new ArraySeq<>(0);

        server = new KafkaServer(kafkaConfig, Time.SYSTEM, threadNamePrefix, metrics);
        server.startup();

        ListenerName listenerName = ListenerName.forSecurityProtocol(SecurityProtocol.PLAINTEXT);
        int port = server.boundPort(listenerName);
        Map<String, Object> producerConfig = new HashMap<>();
        producerConfig.put("bootstrap.servers", "localhost:" + port);
        producerConfig.put("acks", "all");
        producerConfig.put("retries", 0);
        producerConfig.put("batch.size", 16384);
        producerConfig.put("linger.ms", 1);
        producerConfig.put("buffer.memory", 33554432);
        producerConfig.put("key.serializer",
                "org.apache.kafka.common.serialization.StringSerializer");
        producerConfig.put("value.serializer",
                "org.apache.kafka.common.serialization.StringSerializer");

        client = new KafkaProducer<>(producerConfig);

        return LocalResourceInstanceBuilder.builder()
                .resource(server)
                .client(client, KafkaProducer.class)
                .build("kafka", localResource);
    }

    @Override
    public void stop(TestContext testContext,
            LocalResource localResource,
            LocalResourceInstance<KafkaServer, KafkaProducer> instance)
            throws Exception {
        client.close();
        server.shutdown();
        zkServer.close();
    }

}

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
package org.testifyproject.resource.storm;

import org.apache.storm.Config;
import org.apache.storm.ILocalCluster;
import org.apache.storm.generated.AlreadyAliveException;
import org.apache.storm.generated.InvalidTopologyException;
import org.apache.storm.generated.NotAliveException;
import org.apache.storm.testing.TestWordSpout;
import org.apache.storm.topology.TopologyBuilder;
import org.apache.storm.utils.Utils;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import org.testifyproject.ResourceInstance;
import org.testifyproject.TestContext;
import org.testifyproject.annotation.Cut;
import org.testifyproject.annotation.Fixture;
import org.testifyproject.junit4.UnitTest;
import org.testifyproject.resource.fixture.ExclamationTopology;

/**
 *
 * @author saden
 */
@RunWith(UnitTest.class)
public class StormResourceTest {

    @Cut
    @Fixture(destroy = "stop")
    StormResource cut;

    @Test
    public void callToStartResourceShouldReturnRequiredResource()
            throws AlreadyAliveException, NotAliveException, InvalidTopologyException {
        TestContext testContext = mock(TestContext.class);
        given(testContext.getName()).willReturn("test");

        Void config = cut.configure(testContext);
        assertThat(config).isNull();

        ResourceInstance<ILocalCluster, Void> result = cut.start(testContext, config);

        assertThat(result).isNotNull();
        assertThat(result.getClient()).isEmpty();
        assertThat(result.getInstance()).isNotNull();

        ILocalCluster cluster = result.getInstance().getInstance();

        TopologyBuilder builder = new TopologyBuilder();

        builder.setSpout("word", new TestWordSpout(), 10);
        builder.setBolt("exclaim1", new ExclamationTopology.ExclamationBolt(), 3).shuffleGrouping("word");
        builder.setBolt("exclaim2", new ExclamationTopology.ExclamationBolt(), 2).shuffleGrouping("exclaim1");

        Config conf = new Config();
        conf.setDebug(true);
        conf.setNumWorkers(3);

        cluster.submitTopology("exclaim", conf, builder.createTopology());
        Utils.sleep(2000);
        cluster.killTopology("exclaim");
    }

}

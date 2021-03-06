/*
 * Copyright 2014 Open Networking Laboratory
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.onosproject.net.intent.impl;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.onlab.junit.TestUtils;
import org.onosproject.core.IdGenerator;
import org.onosproject.event.Event;
import org.onosproject.net.Link;
import org.onosproject.net.LinkKey;
import org.onosproject.net.NetTestTools;
import org.onosproject.net.NetworkResource;
import org.onosproject.net.intent.Intent;
import org.onosproject.net.intent.IntentBatchLeaderEvent;
import org.onosproject.net.intent.IntentBatchListener;
import org.onosproject.net.intent.IntentId;
import org.onosproject.net.intent.IntentService;
import org.onosproject.net.intent.MockIdGenerator;
import org.onosproject.net.link.LinkEvent;
import org.onosproject.net.resource.LinkResourceEvent;
import org.onosproject.net.resource.LinkResourceListener;
import org.onosproject.net.topology.Topology;
import org.onosproject.net.topology.TopologyEvent;
import org.onosproject.net.topology.TopologyListener;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.SetMultimap;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.onosproject.net.NetTestTools.link;
import org.onlab.junit.TestUtils.TestUtilsException;

/**
 * Tests for the objective tracker.
 */
public class ObjectiveTrackerTest {
    private static final int WAIT_TIMEOUT_SECONDS = 2;
    private Topology topology;
    private ObjectiveTracker tracker;
    private TestTopologyChangeDelegate delegate;
    private List<Event> reasons;
    private TopologyListener listener;
    private LinkResourceListener linkResourceListener;
    private IntentBatchListener leaderListener;
    private IdGenerator mockGenerator;

    /**
     * Initialization shared by all test cases.
     *
     * @throws TestUtilsException if any filed look ups fail
     */
    @Before
    public void setUp() throws TestUtilsException {
        topology = createMock(Topology.class);
        tracker = new ObjectiveTracker();
        delegate = new TestTopologyChangeDelegate();
        tracker.setDelegate(delegate);
        reasons = new LinkedList<>();
        listener = TestUtils.getField(tracker, "listener");
        linkResourceListener = TestUtils.getField(tracker, "linkResourceListener");
        leaderListener = TestUtils.getField(tracker, "leaderListener");
        mockGenerator = new MockIdGenerator();
        Intent.bindIdGenerator(mockGenerator);
    }

    /**
     * Code to clean up shared by all test case.
     */
    @After
    public void tearDown() {
        tracker.unsetDelegate(delegate);
        Intent.unbindIdGenerator(mockGenerator);
    }

    /**
     * Topology change delegate mock that tracks the events coming into it
     * and saves them.  It provides a latch so that tests can wait for events
     * to be generated.
     */
    static class TestTopologyChangeDelegate implements TopologyChangeDelegate {

        CountDownLatch latch = new CountDownLatch(1);
        List<IntentId> intentIdsFromEvent;
        boolean compileAllFailedFromEvent;

        @Override
        public void triggerCompile(Iterable<IntentId> intentIds,
                                   boolean compileAllFailed) {
            intentIdsFromEvent = Lists.newArrayList(intentIds);
            compileAllFailedFromEvent = compileAllFailed;
            latch.countDown();
        }
    }

    /**
     * Mock compilable intent class.
     */
    private static class MockIntent extends Intent {

        public MockIntent(Collection<NetworkResource> resources) {
            super(NetTestTools.APP_ID, resources);
        }

    }

    /**
     * Mock installable intent class.
     */
    private static class MockInstallableIntent extends Intent {
        public MockInstallableIntent(Collection<NetworkResource> resources) {
            super(NetTestTools.APP_ID, resources);
        }

        @Override
        public boolean isInstallable() {
            return true;
        }

    }

    /**
     * Tests an event with no associated reasons.
     *
     * @throws InterruptedException if the latch wait fails.
     */
    @Test
    public void testEventNoReasons() throws InterruptedException {
        final TopologyEvent event = new TopologyEvent(
                TopologyEvent.Type.TOPOLOGY_CHANGED,
                topology,
                null);

        listener.event(event);
        assertThat(
                delegate.latch.await(WAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                is(true));

        assertThat(delegate.intentIdsFromEvent, hasSize(0));
        assertThat(delegate.compileAllFailedFromEvent, is(true));
    }

    /**
     * Tests an event for a link down where none of the reasons match
     * currently installed intents.
     *
     * @throws InterruptedException if the latch wait fails.
     */
    @Test
    public void testEventLinkDownNoMatches() throws InterruptedException {
        final Link link = link("src", 1, "dst", 2);
        final LinkEvent linkEvent = new LinkEvent(LinkEvent.Type.LINK_REMOVED, link);
        reasons.add(linkEvent);

        final TopologyEvent event = new TopologyEvent(
                TopologyEvent.Type.TOPOLOGY_CHANGED,
                topology,
                reasons);

        listener.event(event);
        assertThat(
                delegate.latch.await(WAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                is(true));

        assertThat(delegate.intentIdsFromEvent, hasSize(0));
        assertThat(delegate.compileAllFailedFromEvent, is(false));
    }

    /**
     * Tests an event for a link being added.
     *
     * @throws InterruptedException if the latch wait fails.
     */
    @Test
    public void testEventLinkAdded() throws InterruptedException {
        final Link link = link("src", 1, "dst", 2);
        final LinkEvent linkEvent = new LinkEvent(LinkEvent.Type.LINK_ADDED, link);
        reasons.add(linkEvent);

        final TopologyEvent event = new TopologyEvent(
                TopologyEvent.Type.TOPOLOGY_CHANGED,
                topology,
                reasons);

        listener.event(event);
        assertThat(
                delegate.latch.await(WAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                is(true));

        assertThat(delegate.intentIdsFromEvent, hasSize(0));
        assertThat(delegate.compileAllFailedFromEvent, is(true));
    }

    /**
     * Tests an event for a link down where the link matches existing intents.
     *
     * @throws InterruptedException if the latch wait fails.
     */
    @Test
    public void testEventLinkDownMatch() throws Exception {
        final Link link = link("src", 1, "dst", 2);
        final LinkEvent linkEvent = new LinkEvent(LinkEvent.Type.LINK_REMOVED, link);
        reasons.add(linkEvent);

        final TopologyEvent event = new TopologyEvent(
                TopologyEvent.Type.TOPOLOGY_CHANGED,
                topology,
                reasons);

        final IntentId intentId = IntentId.valueOf(0x333L);
        Collection<NetworkResource> resources = ImmutableSet.of(link);
        tracker.addTrackedResources(intentId, resources);

        listener.event(event);
        assertThat(
                delegate.latch.await(WAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                is(true));

        assertThat(delegate.intentIdsFromEvent, hasSize(1));
        assertThat(delegate.compileAllFailedFromEvent, is(false));
        assertThat(delegate.intentIdsFromEvent.get(0).toString(),
                   equalTo("0x333"));
    }

    /**
     * Tests a resource available event.
     *
     * @throws InterruptedException if the latch wait fails.
     */
    @Test
    public void testResourceEvent() throws Exception {
        LinkResourceEvent event = new LinkResourceEvent(
                LinkResourceEvent.Type.ADDITIONAL_RESOURCES_AVAILABLE,
                new HashSet<>());
        linkResourceListener.event(event);

        assertThat(
                delegate.latch.await(WAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                is(true));

        assertThat(delegate.intentIdsFromEvent, hasSize(0));
        assertThat(delegate.compileAllFailedFromEvent, is(true));
    }

    /**
     * Tests leadership events.
     *
     * @throws InterruptedException if the latch wait fails.
     */
    @Test
    public void testLeaderEvents() throws Exception {

        final Link link = link("src", 1, "dst", 2);
        final List<NetworkResource> resources = ImmutableList.of(link);

        final List<Intent> intents = new LinkedList<>();
        final List<Intent> installableIntents = new LinkedList<>();
        installableIntents.add(new MockInstallableIntent(resources));
        intents.add(new MockIntent(resources));

        final SetMultimap<LinkKey, IntentId> intentsByLink =
                TestUtils.getField(tracker, "intentsByLink");
        assertThat(intentsByLink.size(), is(0));

        final IntentService mockIntentManager = createMock(IntentService.class);
        expect(mockIntentManager
                .getIntents())
                .andReturn(intents)
                .anyTimes();
        expect(mockIntentManager
                .getIntent(IntentId.valueOf(0x0)))
                .andReturn(intents.get(0))
                .anyTimes();
        expect(mockIntentManager
                .getInstallableIntents(IntentId.valueOf(0x1)))
                .andReturn(installableIntents)
                .anyTimes();
        replay(mockIntentManager);
        tracker.bindIntentService(mockIntentManager);

        final IntentBatchLeaderEvent electedEvent = new IntentBatchLeaderEvent(
                IntentBatchLeaderEvent.Type.ELECTED, NetTestTools.APP_ID);
        leaderListener.event(electedEvent);
        assertThat(intentsByLink.size(), is(1));

        final IntentBatchLeaderEvent bootedEvent = new IntentBatchLeaderEvent(
                IntentBatchLeaderEvent.Type.BOOTED, NetTestTools.APP_ID);
        leaderListener.event(bootedEvent);
        assertThat(intentsByLink.size(), is(0));

        tracker.unbindIntentService(mockIntentManager);
    }
}

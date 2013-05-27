/*
 Copyright 2005 Dan Creswell (dan@dancres.org)

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at
 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 License for the specific language governing permissions and limitations under
 the License.
*/

package org.dancres.blitz.jini.lockmgr;

import java.io.Serializable;

import org.jgroups.JChannel;
import org.jgroups.Channel;

import org.jgroups.blocks.LockNotGrantedException;
import org.jgroups.blocks.LockNotReleasedException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.assertTrue;

/**
   Modified to test additional features such as leasing

   @author Dan Creswell (dan@dancres.org)
 */
public class DistributedLockManagerTest {
	final private static Logger log = LoggerFactory.getLogger(DistributedLockManager.class);

    public static final String SERVER_PROTOCOL_STACK = ""
            + "UDP(mcast_addr=228.3.11.76;mcast_port=12345;ip_ttl=1;"
            + "mcast_send_buf_size=150000;mcast_recv_buf_size=80000)"
//        + "JMS(topicName=topic/testTopic;cf=UILConnectionFactory;"
//        + "jndiCtx=org.jnp.interfaces.NamingContextFactory;"
//        + "providerURL=localhost;ttl=10000)"
            + ":PING(timeout=500;num_initial_members=2)"
            + ":FD"
            + ":VERIFY_SUSPECT(timeout=1500)" 
            + ":pbcast.NAKACK(gc_lag=50;retransmit_timeout=300,600,1200,2400,4800)"
            + ":UNICAST(timeout=5000)"
            + ":pbcast.STABLE(desired_avg_gossip=200)"
            + ":FRAG(frag_size=4096)"
            + ":pbcast.GMS(join_timeout=5000;join_retry_timeout=1000;"
            +     "shun=false;print_local_addr=false)"
//        + ":SPEED_LIMIT(down_queue_limit=10)"
            + ":pbcast.STATE_TRANSFER(down_thread=false)"
        ;
    
    private JChannel channel1;
    private JChannel channel2;

    protected VotingAdapter adapter1;
    protected VotingAdapter adapter2;
    
    protected LockManager lockManager1;
    protected LockManager lockManager2;

    protected static
            boolean logConfigured;

	@Before
    public void setUp() throws Exception {
        


        channel1 = new JChannel(SERVER_PROTOCOL_STACK);
        channel1.setOpt(Channel.GET_STATE_EVENTS, Boolean.TRUE);
        channel1.setOpt(Channel.AUTO_RECONNECT, Boolean.TRUE);
        channel1.setOpt(Channel.AUTO_GETSTATE, Boolean.TRUE);
        adapter1 = new VotingAdapter(channel1);
        channel1.connect("voting");

        while (!channel1.isConnected()) {
            try {
                Thread.sleep(5000);
            } catch (InterruptedException anIE) {
            }
        }

		log.info("Channel1 connected");

        // give some time for the channel to become a coordinator
        try {
                Thread.sleep(5000);
        } catch(Exception ex) {
        }

		log.info("Request state on channel1: {}",
				channel1.getState(null, 0));
        
        lockManager1 = new DistributedLockManager(adapter1, "1");

        channel2 = new JChannel(SERVER_PROTOCOL_STACK);
        channel2.setOpt(Channel.GET_STATE_EVENTS, Boolean.TRUE);
        channel2.setOpt(Channel.AUTO_RECONNECT, Boolean.TRUE);
        channel2.setOpt(Channel.AUTO_GETSTATE, Boolean.TRUE);
        adapter2 = new VotingAdapter(channel2);
        lockManager2 = new DistributedLockManager(adapter2, "2");
        
        channel2.connect("voting");
        while (!channel2.isConnected()) {
            try {
                Thread.sleep(5000);
            } catch (InterruptedException anIE) {
            }
        }

		log.info("Channel2 connected");

		log.info("Request state on channel2: {}",
				channel2.getState(null, 0));
    }

	@After
    public void tearDown() throws Exception {
        channel2.close();
        
        try {
            Thread.sleep(1000);
        }
        catch (InterruptedException ex) {
        }
        
        
        channel1.close();
    }

	@Test
    public void test() throws Exception {
		log.info("Lock 1");
        lockManager1.lock("obj1", "owner1", 10000, null);
        
        try {
			log.info("Lock 1.1");
            lockManager1.lock("obj1", "owner2", 10000, null);
            assertTrue("obj1 should not be locked.", false);
        } catch (LockNotGrantedException ex) {
            // everything is ok
        }

        try {
			log.info("Insert 1.1.1");
            lockManager1.insert("obj1", 10000, 25);
            assertTrue("resource should not be inserted(lock).", false);
        } catch (LockNotGrantedException ex) {
            // okay
        }

		log.info("Lock 2");
        lockManager2.lock("obj2", "owner2", 1000, null);

		log.info("UnLock 1");
        lockManager1.unlock("obj1", "owner1", null);

        try {
			log.info("Insert 1.1.2");
            lockManager1.insert("obj1", 10000, 25);
            // okay
        } catch (LockNotGrantedException ex) {
            assertTrue("resource should be inserted(unlock).", false);
        }
        
        Serializable myResource = lockManager1.getResource("obj1");

        if (myResource == null)
            assertTrue("No resource recovered", false);
        else
			log.info("Recovered resource: {}", myResource);

        try {
			log.info("UnLock 2");
            lockManager1.unlock("obj2", "owner1", null);
            assertTrue("obj2 should not be released.", false);
        }
        catch (LockNotReleasedException ex) {
            // everything is ok
        }

		log.info("UnLock 2");
        lockManager1.unlock("obj2", "owner2", null);

        lockManager1.lock("obj3", "owner4", 10000, null);

        JChannel channel3 = new JChannel(SERVER_PROTOCOL_STACK);
        channel3.setOpt(Channel.GET_STATE_EVENTS, Boolean.TRUE);
        channel3.setOpt(Channel.AUTO_RECONNECT, Boolean.TRUE);
        channel3.setOpt(Channel.AUTO_GETSTATE, Boolean.TRUE);
        VotingAdapter adapter3 = new VotingAdapter(channel3);
        LockManager lockManager3 = new DistributedLockManager(adapter3, "3");
        
        channel3.connect("voting");

        while (!channel3.isConnected()) {
            try {
                Thread.sleep(5000);
            } catch (InterruptedException anIE) {
            }
        }
		log.info("Channel3 connected");

        while (! channel3.getState(null, 0));

        try {
            lockManager3.unlock("obj3", "owner4", null);
        } catch (LockNotReleasedException ex) {
            // failed
            assertTrue("Lock should be released.", false);
        }

        try {
			log.info("Delete 1.1.1");
            lockManager3.delete("obj1", 10000);
            // okay
        } catch (LockNotReleasedException ex) {
            assertTrue("resource should be inserted(unlock).", false);
        }
    }
}

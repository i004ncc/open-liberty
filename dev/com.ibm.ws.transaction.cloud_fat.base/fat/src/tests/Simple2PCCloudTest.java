/*******************************************************************************
 * Copyright (c) 2019, 2024 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package tests;

import static org.junit.Assert.assertNotNull;

import java.io.IOException;
import java.util.Collections;
import java.util.ListIterator;

import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.ibm.tx.jta.ut.util.LastingXAResourceImpl;
import com.ibm.tx.jta.ut.util.XAResourceImpl;
import com.ibm.websphere.simplicity.ProgramOutput;
import com.ibm.websphere.simplicity.ShrinkHelper;
import com.ibm.websphere.simplicity.ShrinkHelper.DeployOptions;
import com.ibm.websphere.simplicity.config.ConfigElementList;
import com.ibm.websphere.simplicity.config.Fileset;
import com.ibm.websphere.simplicity.config.ServerConfiguration;
import com.ibm.websphere.simplicity.log.Log;
import com.ibm.ws.transaction.fat.util.FATUtils;

import componenttest.annotation.AllowedFFDC;
import componenttest.annotation.ExpectedFFDC;
import componenttest.annotation.Server;
import componenttest.custom.junit.runner.FATRunner;
import componenttest.topology.impl.LibertyServer;

@RunWith(FATRunner.class)
public class Simple2PCCloudTest extends CloudTestBase {

    public static final String APP_NAME = "transaction";
    public static final String SERVLET_NAME = APP_NAME + "/Simple2PCCloudServlet";
    protected static final int cloud2ServerPort = 9992;

    @Server("com.ibm.ws.transaction_CLOUD001")
    public static LibertyServer s1;

    @Server("com.ibm.ws.transaction_CLOUD002")
    public static LibertyServer s2;

    @Server("longLeaseLengthServer1")
    public static LibertyServer longLeaseLengthServer1;

    @Server("peerPrecedenceServer1")
    public static LibertyServer peerPrecedenceServer1;

    @BeforeClass
    public static void setUp() throws Exception {

        server1 = s1;
        server2 = s2;

        final WebArchive app = ShrinkHelper.buildDefaultApp(APP_NAME, "servlets.*");
        final DeployOptions[] dO = new DeployOptions[0];

        ShrinkHelper.exportAppToServer(server1, app, dO);
        ShrinkHelper.exportAppToServer(server2, app, dO);
        ShrinkHelper.exportAppToServer(longLeaseLengthServer1, app, dO);
        ShrinkHelper.exportAppToServer(peerPrecedenceServer1, app, dO);

        server1.setServerStartTimeout(FATUtils.LOG_SEARCH_TIMEOUT);
        server2.setServerStartTimeout(FATUtils.LOG_SEARCH_TIMEOUT);
        longLeaseLengthServer1.setServerStartTimeout(FATUtils.LOG_SEARCH_TIMEOUT);
        peerPrecedenceServer1.setServerStartTimeout(FATUtils.LOG_SEARCH_TIMEOUT);
    }

    /**
     * Test access to the Lease table.
     *
     * This is a readiness check to verify that resources are available and accessible.
     *
     * @throws Exception
     */
    @Test
    public void testLeaseTableAccess() throws Exception {
        final String method = "testLeaseTableAccess";
        StringBuilder sb = null;
        String id = "001";

        // Start Server1
        FATUtils.startServers(server1);

        try {
            sb = runTestWithResponse(server1, SERVLET_NAME, "testLeaseTableAccess");

        } catch (Throwable e) {
        }
        Log.info(this.getClass(), method, "testLeaseTableAccess" + id + " returned: " + sb);

        // "CWWKE0701E" error message is allowed
        FATUtils.stopServers(new String[] { "CWWKE0701E" }, server1);
    }

    /**
     * The purpose of this test is as a control to verify that single server recovery is working.
     *
     * The Cloud001 server is started and halted by a servlet that leaves an indoubt transaction.
     * Cloud001 is restarted and transaction recovery verified.
     *
     * @throws Exception
     */
    @Test
    @AllowedFFDC(value = { "javax.transaction.xa.XAException", "com.ibm.ws.recoverylog.spi.RecoveryFailedException" })
    public void testDBBaseRecovery() throws Exception {
        final String method = "testDBBaseRecovery";
        StringBuilder sb = null;
        String id = "001";
        // Start Server1
        FATUtils.startServers(server1);

        try {
            // We expect this to fail since it is gonna crash the server
            sb = runTestWithResponse(server1, SERVLET_NAME, "setupRec" + id);
        } catch (Throwable e) {
        }
        Log.info(this.getClass(), method, "setupRec" + id + " returned: " + sb);

        server1.waitForStringInLog(XAResourceImpl.DUMP_STATE);

        // Now re-start cloud1
        ProgramOutput po = server1.startServerAndValidate(false, true, true);
        if (po.getReturnCode() != 0) {
            Log.info(this.getClass(), method, po.getCommand() + " returned " + po.getReturnCode());
            Log.info(this.getClass(), method, "Stdout: " + po.getStdout());
            Log.info(this.getClass(), method, "Stderr: " + po.getStderr());
            Exception ex = new Exception("Could not restart the server");
            Log.error(this.getClass(), "recoveryTest", ex);
            throw ex;
        }

        // Server appears to have started ok. Check for key string to see whether recovery has succeeded
        server1.waitForStringInTrace("Performed recovery for cloud001");

        // Lastly stop server1
        // "WTRN0075W", "WTRN0076W", "CWWKE0701E" error messages are expected/allowed
        FATUtils.stopServers(new String[] { "WTRN0075W", "WTRN0076W", "CWWKE0701E" }, server1);

        // Lastly, clean up XA resource file
        server1.deleteFileFromLibertyInstallRoot("/usr/shared/" + LastingXAResourceImpl.STATE_FILE_ROOT);
    }

    /**
     * The purpose of this test is to verify simple peer transaction recovery.
     *
     * The Cloud001 server is started and halted by a servlet that leaves an indoubt transaction.
     * Cloud002, a peer server as it belongs to the same recovery group is started and recovery the
     * transaction that belongs to Cloud001.
     *
     * @throws Exception
     */
    @Test
    @AllowedFFDC(value = { "com.ibm.ws.recoverylog.spi.RecoveryFailedException" })
    public void testDBRecoveryTakeover() throws Exception {
        final String method = "testDBRecoveryTakeover";
        StringBuilder sb = null;
        String id = "001";
        // Start Server1
        FATUtils.startServers(server1);

        try {
            // We expect this to fail since it is gonna crash the server
            sb = runTestWithResponse(server1, SERVLET_NAME, "setupRec" + id);
        } catch (Throwable e) {
        }
        Log.info(this.getClass(), method, "setupRec" + id + " returned: " + sb);

        server1.waitForStringInLog(XAResourceImpl.DUMP_STATE);

        // Now start server2
        server2.setHttpDefaultPort(cloud2ServerPort);
        ProgramOutput po = server2.startServerAndValidate(false, true, true);
        final int status = po.getReturnCode();
        // This test has shown that 22 is success sometimes
        if (status != 0 && status != 22) {
            Log.info(this.getClass(), method, po.getCommand() + " returned " + po.getReturnCode());
            Log.info(this.getClass(), method, "Stdout: " + po.getStdout());
            Log.info(this.getClass(), method, "Stderr: " + po.getStderr());
            Exception ex = new Exception("Could not restart the server");
            Log.error(this.getClass(), "recoveryTest", ex);
            throw ex;
        }

        // Server appears to have started ok. Check for key string to see whether peer recovery has succeeded
        assertNotNull(server2.waitForStringInTrace("Performed recovery for cloud001", FATUtils.LOG_SEARCH_TIMEOUT));
        // "CWWKE0701E" error message is allowed
        FATUtils.stopServers(new String[] { "CWWKE0701E" }, server2);

        // Lastly, clean up XA resource files
        server1.deleteFileFromLibertyInstallRoot("/usr/shared/" + LastingXAResourceImpl.STATE_FILE_ROOT);
    }

    /**
     * The purpose of this test is to verify correct behaviour when peer servers compete for a log.
     *
     * The Cloud001 server is started and a servlet invoked. The servlet modifies the owner of the server's
     * lease recored in the lease table. This simulates the situation where a peer server has acquired the
     * ownership of the lease and is recovering Cloud001's logs. Finally the servlet halts the server leaving
     * an indoubt transaction.
     *
     * The peerRecoveryPrecedence server.xml attribute is set to "true" to enable the "original" behaviour.
     * So Cloud001 is restarted but should fail to acquire the lease to its recovery logs as it is no longer the owner.
     *
     * @throws Exception
     */
    @Test
    @ExpectedFFDC(value = { "com.ibm.ws.recoverylog.spi.RecoveryFailedException" })
    @AllowedFFDC(value = { "javax.transaction.xa.XAException", "com.ibm.tx.jta.XAResourceNotAvailableException", "com.ibm.ws.recoverylog.spi.RecoveryFailedException",
                           "java.lang.IllegalStateException" })
    // defect 227411, if cloud002 starts slowly, then access to cloud001's indoubt tx
    // XAResources may need to be retried (tx recovery is, in such cases, working as designed.
    public void testDBRecoveryCompeteForLogPeerPrecedence() throws Exception {
        final String method = "testDBRecoveryCompeteForLogPeerPrecedence";
        StringBuilder sb = null;
        String id = "001";

        // Start peerPrecedenceServer1
        FATUtils.startServers(peerPrecedenceServer1);

        try {
            sb = runTestWithResponse(peerPrecedenceServer1, SERVLET_NAME, "modifyLeaseOwner");

            // We expect this to fail since it is gonna crash the server
            sb = runTestWithResponse(peerPrecedenceServer1, SERVLET_NAME, "setupRec" + id);
        } catch (Throwable e) {
        }
        Log.info(this.getClass(), method, "setupRec" + id + " returned: " + sb);

        assertNotNull(peerPrecedenceServer1.getServerName() + " didn't crash properly", peerPrecedenceServer1.waitForStringInLog(XAResourceImpl.DUMP_STATE));
        peerPrecedenceServer1.postStopServerArchive(); // must explicitly collect since crashed server
        // Need to ensure we have a long (5 minute) timeout for the lease, otherwise we may decide that we CAN delete
        // and renew our own lease. longLeasLengthServer1 is a clone of server1 with a longer lease length.

        // Now re-start server1 but we fully expect this to fail
        try {
            // The server has been halted but its status variable won't have been reset because we crashed it. In order to
            // setup the server for a restart, set the server state manually.
            peerPrecedenceServer1.setStarted(false);
            peerPrecedenceServer1.startServerExpectFailure("recovery-dblog-fail.log", false, true);
        } catch (Exception ex) {
            // Tolerate an exception here, as recovery is asynch and the "successful start" message
            // may have been produced by the main thread before the recovery thread had completed
            Log.info(this.getClass(), method, "startServerExpectFailure threw exc: " + ex);
        }
        // Pull in a new server.xml file that ensures that we have a long (5 minute) timeout
        // for the lease, otherwise we may decide that we CAN delete and renew our own lease.

        // Server appears to have failed as expected. Check for log failure string
        if (peerPrecedenceServer1.waitForStringInLog("RECOVERY_LOG_FAILED") == null) {
            Exception ex = new Exception("Recovery logs should have failed");
            Log.error(this.getClass(), "recoveryTestCompeteForLock", ex);
            throw ex;
        }
        peerPrecedenceServer1.postStopServerArchive();
        // defect 210055: Now start cloud2 so that we can tidy up the environment, otherwise cloud1
        // is unstartable because its lease is owned by cloud2.
        server2.setHttpDefaultPort(cloud2ServerPort);
        ProgramOutput po = server2.startServerAndValidate(false, true, true);
        if (po.getReturnCode() != 0) {
            Log.info(this.getClass(), method, po.getCommand() + " returned " + po.getReturnCode());
            Log.info(this.getClass(), method, "Stdout: " + po.getStdout());
            Log.info(this.getClass(), method, "Stderr: " + po.getStderr());
            Exception ex = new Exception("Could not restart the server");
            Log.error(this.getClass(), "recoveryTest", ex);
            throw ex;
        }

        // Server appears to have started ok. Check for 2 key strings to see whether peer recovery has succeeded
        server2.waitForStringInTrace("Performed recovery for cloud001");
        // "CWWKE0701E" error message is allowed
        FATUtils.stopServers(new String[] { "CWWKE0701E" }, server2);

        // Lastly, clean up XA resource files
        server1.deleteFileFromLibertyInstallRoot("/usr/shared/" + LastingXAResourceImpl.STATE_FILE_ROOT);
    }

    /**
     * The purpose of this test is to verify correct behaviour when peer servers compete for a log.
     *
     * The Cloud001 server is started and a servlet invoked. The servlet modifies the owner of the server's
     * lease recored in the lease table. This simulates the situation where a peer server has acquired the
     * ownership of the lease and is recovering Cloud001's logs. Finally the servlet halts the server leaving
     * an indoubt transaction.
     *
     * The default behaviour is that Cloud001 is restarted and wil aggressively acquire the lease to its recovery logs
     * even though it is not the owner.
     *
     * @throws Exception
     */
    @Test
    @AllowedFFDC(value = { "javax.transaction.xa.XAException", "com.ibm.tx.jta.XAResourceNotAvailableException", "com.ibm.ws.recoverylog.spi.RecoveryFailedException",
                           "java.lang.IllegalStateException" })
    // defect 227411, if cloud002 starts slowly, then access to cloud001's indoubt tx
    // XAResources may need to be retried (tx recovery is, in such cases, working as designed.
    public void testDBRecoveryCompeteForLog() throws Exception {
        final String method = "testDBRecoveryCompeteForLog";
        StringBuilder sb = null;
        String id = "001";

        // Start peerPrecedenceServer1
        FATUtils.startServers(server1);

        try {
            sb = runTestWithResponse(server1, SERVLET_NAME, "modifyLeaseOwner");

            // We expect this to fail since it is gonna crash the server
            sb = runTestWithResponse(server1, SERVLET_NAME, "setupRec" + id);
        } catch (Throwable e) {
        }
        Log.info(this.getClass(), method, "setupRec" + id + " returned: " + sb);

        assertNotNull(server1.getServerName() + " didn't crash properly", server1.waitForStringInLog(XAResourceImpl.DUMP_STATE));
        server1.postStopServerArchive(); // must explicitly collect since crashed server
        // Need to ensure we have a long (5 minute) timeout for the lease, otherwise we may decide that we CAN delete
        // and renew our own lease. longLeasLengthServer1 is a clone of server1 with a longer lease length.

        // Now re-start "server1" with a long lease length to ensure any lease claim is at startup only
        ProgramOutput po = longLeaseLengthServer1.startServerAndValidate(false, true, true);
        if (po.getReturnCode() != 0) {
            Log.info(this.getClass(), method, po.getCommand() + " returned " + po.getReturnCode());
            Log.info(this.getClass(), method, "Stdout: " + po.getStdout());
            Log.info(this.getClass(), method, "Stderr: " + po.getStderr());
            Exception ex = new Exception("Could not restart the server");
            Log.error(this.getClass(), "recoveryTest", ex);
            throw ex;
        }

        // Server appears to have started ok. Check for key string to see whether recovery has succeeded
        longLeaseLengthServer1.waitForStringInTrace("Performed recovery for cloud001");

        // "CWWKE0701E" error message is allowed
        FATUtils.stopServers(new String[] { "CWWKE0701E" }, longLeaseLengthServer1);

        // Lastly, clean up XA resource files
        server1.deleteFileFromLibertyInstallRoot("/usr/shared/" + LastingXAResourceImpl.STATE_FILE_ROOT);
    }

    @Test
    @AllowedFFDC(value = { "javax.resource.spi.ResourceAllocationException", "java.sql.SQLNonTransientConnectionException", "javax.resource.ResourceException",
                           "com.ibm.ws.rsadapter.exceptions.DataStoreAdapterException" })
    public void datasourceChangeTest() throws Exception {
        final String method = "datasourceChangeTest";

        serversToCleanup = new LibertyServer[] { server1 };

        // Start Server1
        FATUtils.startServers(server1);
        // Update the server configuration on the fly to force the invalidation of the DataSource
        ServerConfiguration config = server1.getServerConfiguration();
        ConfigElementList<Fileset> fsConfig = config.getFilesets();
        Log.info(this.getClass(), method, "retrieved fileset config " + fsConfig);
        String sfDirOrig = null;

        Fileset fs = null;
        ListIterator<Fileset> lItr = fsConfig.listIterator();
        while (lItr.hasNext()) {
            fs = lItr.next();
            Log.info(this.getClass(), method, "retrieved fileset " + fs);
            sfDirOrig = fs.getDir();

            Log.info(this.getClass(), method, "retrieved Dir " + sfDirOrig);
            fs.setDir(sfDirOrig + "2");
        }

        assertNotNull("Couldn't set test config", fs);

        server1.setMarkToEndOfLog();
        server1.updateServerConfiguration(config);
        server1.waitForConfigUpdateInLogUsingMark(Collections.singleton(APP_NAME));

        Log.info(this.getClass(), method, "Reset the config back the way it originally was");
        fs.setDir(sfDirOrig);

        server1.setMarkToEndOfLog();
        server1.updateServerConfiguration(config);
        server1.waitForConfigUpdateInLogUsingMark(Collections.singleton(APP_NAME));

        // Should see a message like
        // WTRN0108I: Have recovered from ResourceAllocationException in SQL RecoveryLog partnerlog for server recovery.dblog
        assertNotNull("No warning message signifying failover", server1.waitForStringInLog("Have recovered from ResourceAllocationException"));
    }

    @Test
    @ExpectedFFDC(value = { "javax.resource.spi.ResourceAllocationException" })
    @AllowedFFDC(value = { "java.sql.SQLNonTransientConnectionException",
                           "javax.resource.ResourceException", "com.ibm.ws.rsadapter.exceptions.DataStoreAdapterException" })
    public void datasourceChangeTest2() throws Exception {
        final String method = "datasourceChangeTest2";

        serversToCleanup = new LibertyServer[] { server1 };

        // Start Server1
        FATUtils.startServers(server1);
        // Update the server configuration on the fly to force the invalidation of the DataSource
        ServerConfiguration config = server1.getServerConfiguration();
        ConfigElementList<Fileset> fsConfig = config.getFilesets();
        Log.info(this.getClass(), method, "retrieved fileset config " + fsConfig);
        String sfDirOrig = "";

        Fileset fs = null;
        ListIterator<Fileset> lItr = fsConfig.listIterator();
        while (lItr.hasNext()) {
            fs = lItr.next();
            Log.info(this.getClass(), method, "retrieved fileset " + fs);
            sfDirOrig = fs.getDir();

            Log.info(this.getClass(), method, "retrieved Dir " + sfDirOrig);
            fs.setDir(sfDirOrig + "2");
        }

        assertNotNull("Couldn't set test config", fs);

        server1.setMarkToEndOfLog();
        server1.updateServerConfiguration(config);
        server1.waitForConfigUpdateInLogUsingMark(Collections.singleton(APP_NAME));

        Log.info(this.getClass(), method, "Reset the config back the way it originally was");
        fs.setDir(sfDirOrig);

        server1.setMarkToEndOfLog();
        server1.updateServerConfiguration(config);
        server1.waitForConfigUpdateInLogUsingMark(Collections.singleton(APP_NAME));

        // Should see a message like
        // WTRN0108I: Have recovered from ResourceAllocationException in SQL RecoveryLog partnerlog for server recovery.dblog
        assertNotNull("No warning message signifying failover", server1.waitForStringInLog("Have recovered from ResourceAllocationException"));

        String id = "007";

        try {
            // We expect this to fail since it is gonna crash the server
            runTest(server1, SERVLET_NAME, "setupRec" + id);
        } catch (IOException e) {
        }

        server1.waitForStringInLog(XAResourceImpl.DUMP_STATE);
        server1.resetStarted();

        // Now re-start cloud1
        FATUtils.startServers(server1);

        // Server appears to have started ok. Check for key string to see whether recovery has succeeded
        server1.waitForStringInTrace("Performed recovery for cloud0011");

        runTest(server1, SERVLET_NAME, "checkRec" + id);
    }

    @Test
    @ExpectedFFDC(value = { "javax.resource.spi.ResourceAllocationException" })
    @AllowedFFDC(value = { "java.sql.SQLNonTransientConnectionException",
                           "javax.resource.ResourceException", "com.ibm.ws.rsadapter.exceptions.DataStoreAdapterException" })
    public void datasourceChangeTest3() throws Exception {
        final String method = "datasourceChangeTest2";

        serversToCleanup = new LibertyServer[] { server1 };

        // Start Server1
        FATUtils.startServers(server1);
        // Update the server configuration on the fly to force the invalidation of the DataSource
        ServerConfiguration config = server1.getServerConfiguration();
        ConfigElementList<Fileset> fsConfig = config.getFilesets();
        Log.info(this.getClass(), method, "retrieved fileset config " + fsConfig);
        String sfDirOrig = null;

        Fileset fs = null;
        ListIterator<Fileset> lItr = fsConfig.listIterator();
        while (lItr.hasNext()) {
            fs = lItr.next();
            Log.info(this.getClass(), method, "retrieved fileset " + fs);
            sfDirOrig = fs.getDir();

            Log.info(this.getClass(), method, "retrieved Dir " + sfDirOrig);
            fs.setDir(sfDirOrig + "2");
        }

        assertNotNull("Couldn't set test config", fs);

        server1.setMarkToEndOfLog();
        server1.updateServerConfiguration(config);
        server1.waitForConfigUpdateInLogUsingMark(Collections.singleton(APP_NAME));

        Log.info(this.getClass(), method, "Reset the config back the way it originally was");

        // Should see a message like
        // WTRN0108I: Have recovered from ResourceAllocationException in SQL RecoveryLog partnerlog for server recovery.dblog
        assertNotNull("No warning message signifying failover", server1.waitForStringInLog("Have recovered from ResourceAllocationException"));

        String id = "007";

        try {
            // We expect this to fail since it is gonna crash the server
            runTest(server1, SERVLET_NAME, "setupRec" + id);
        } catch (IOException e) {
        }

        server1.waitForStringInLog(XAResourceImpl.DUMP_STATE);
        server1.resetStarted();

        // Now re-start cloud1
        FATUtils.startServers(server1);

        // Server appears to have started ok. Check for key string to see whether recovery has succeeded
        server1.waitForStringInTrace("Performed recovery for cloud0011");

        runTest(server1, SERVLET_NAME, "checkRec" + id);

        // Test has passed at this point

        Log.info(this.getClass(), method, "Reset the config back the way it originally was");
        fs.setDir(sfDirOrig);

        server1.setMarkToEndOfLog();
        server1.updateServerConfiguration(config);
        server1.waitForConfigUpdateInLogUsingMark(Collections.singleton(APP_NAME));
    }
}
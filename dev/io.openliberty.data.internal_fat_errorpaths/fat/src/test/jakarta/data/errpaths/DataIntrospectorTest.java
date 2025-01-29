/*******************************************************************************
 * Copyright (c) 2025 IBM Corporation and others.
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
package test.jakarta.data.errpaths;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;

import componenttest.custom.junit.runner.FATRunner;
import componenttest.topology.utils.FATServletClient;

/**
 * Contains tests that make assertions on the Jakarta Data introspector output.
 * The introspector runs at the end of DataErrPathsTest before stopping the server
 * so that it contains the state of a running server, and guaranteeing that it is
 * available to these tests.
 */
@RunWith(FATRunner.class)
public class DataIntrospectorTest extends FATServletClient {
    /**
     * Jakarta Data introspector output is captured during DataErrPathsTest.tearDown
     */
    static final List<String> introspectorOutput = new ArrayList<>();

    /**
     * Asserts that a line is found within the Jakarta Data introspector output.
     *
     * @param expected the line to search for.
     */
    private static void assertLineFound(String expected) {
        if (introspectorOutput.isEmpty())
            fail("JakartaDataIntrospector output not found. Unable to run test. " +
                 "Check server logs for errors.");

        if (!introspectorOutput.contains(expected))
            fail("Information not found in introspector output. " +
                 "To view introspector output from the test results page, " +
                 "follow the System.out link and then search for " +
                 "testIntrospectorOutputObtained. Missing line is: " +
                 expected);
    }

    /**
     * Verify that introspector output was obtained.
     */
    @Test
    public void testIntrospectorOutputObtained() {
        assertEquals(false, introspectorOutput.isEmpty());

        // To view output, from the test results page, follow the System.out link
        // and search for "testIntrospectorOutputObtained"
        for (String line : introspectorOutput)
            System.out.println(line);
    }

    /**
     * Verify that introspector output contains the config display id of
     * databaseStore elements that are used by repositories.
     */
    @Test
    public void testOutputContainsDatabaseStore() {
        assertLineFound("    for databaseStore application[DataErrPathsTestApp]" +
                        "/databaseStore[java:app/jdbc/DerbyDataSource]");

        assertLineFound("    for databaseStore application[DataErrPathsTestApp]" +
                        "/module[DataErrPathsTestApp.war]" +
                        "/databaseStore[java:comp/jdbc/InvalidDatabase]");
    }

    /**
     * Verify that introspector output contains the dataStore value that is
     * configured on the repository.
     */
    @Test
    public void testOutputContainsDataStore() {
        assertLineFound("        dataStore: AbsentFromConfig");
        assertLineFound("        dataStore: java:app/env/WrongPersistenceUnitRef");
        assertLineFound("        dataStore: java:app/jdbc/DerbyDataSource");
        assertLineFound("        dataStore: java:comp/DefaultDataSource");
        assertLineFound("        dataStore: java:comp/jdbc/InvalidDatabase");
        assertLineFound("        dataStore: java:module/env/DoesNotExist");
        assertLineFound("        dataStore: java:module/jdbc/DataSourceForInvalidEntity");
    }

    /**
     * Verify that introspector output contains the name of the module or
     * application that defines the repository.
     */
    @Test
    public void testOutputContainsDefiningArtifact() {
        assertLineFound("        defining artifact: DataErrPathsTestApp#DataErrPathsTestApp.war");
    }

    /**
     * Verify that introspector output contains the entity class that is used
     * by the repository.
     */
    @Test
    public void testOutputContainsEntity() {
        assertLineFound("        entity: test.jakarta.data.errpaths.web.Voter");
    }

    /**
     * Verify that introspector output contains the name of the primary entity class.
     */
    @Test
    public void testOutputContainsPrimaryEntity() {
        assertLineFound("      primary entity: test.jakarta.data.errpaths.web.Invention");
        assertLineFound("      primary entity: test.jakarta.data.errpaths.web.Volunteer");
        assertLineFound("      primary entity: test.jakarta.data.errpaths.web.Voter");
    }

    /**
     * Verify that introspector output contains the name of repository interfaces.
     */
    @Test
    public void testOutputContainsRepository() {
        assertLineFound("      repository: test.jakarta.data.errpaths.web.InvalidDatabaseRepo");
        assertLineFound("      repository: test.jakarta.data.errpaths.web.InvalidJNDIRepo");
        assertLineFound("      repository: test.jakarta.data.errpaths.web.InvalidNonJNDIRepo");
        assertLineFound("      repository: test.jakarta.data.errpaths.web.Inventions");
        assertLineFound("      repository: test.jakarta.data.errpaths.web.RepoWithoutDataStore");
        assertLineFound("      repository: test.jakarta.data.errpaths.web.Voters");
        assertLineFound("      repository: test.jakarta.data.errpaths.web.WrongPersistenceUnitRefRepo");
    }
}

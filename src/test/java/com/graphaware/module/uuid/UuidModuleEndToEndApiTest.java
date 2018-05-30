/*
 * Copyright (c) 2013-2017 GraphAware
 *
 * This file is part of the GraphAware Framework.
 *
 * GraphAware Framework is free software: you can redistribute it and/or modify it under the terms of
 * the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details. You should have received a copy of
 * the GNU General Public License along with this program.  If not, see
 * <http://www.gnu.org/licenses/>.
 */

package com.graphaware.module.uuid;

import com.graphaware.test.integration.GraphAwareIntegrationTest;
import org.junit.Test;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.apache.http.HttpStatus.SC_OK;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

public class UuidModuleEndToEndApiTest extends GraphAwareIntegrationTest {

    public static final Pattern UUID_PATTERN = Pattern.compile("\\\"uuid\\\":\\\"([a-zA-Z0-9-]*)\\\"");
    public static final Pattern CREATED_AT_PATTERN = Pattern.compile("\\\"createdAt\\\":([0-9]+)");
    public static final Pattern UPDATED_AT_PATTERN = Pattern.compile("\\\"updatedAt\\\":([0-9]+)");

    @Override
    protected String configFile() {
        return "neo4j-uuid-all.conf";
    }

    @Test
    public void testWorkflow() {
        //Create & Assign
        httpClient.executeCypher(baseNeoUrl(), "CREATE (p:Person {name:'Luanne'})");

        String response = httpClient.executeCypher(baseNeoUrl(), "MATCH (p:Person) RETURN p");

        Matcher matcher = UUID_PATTERN.matcher(response);
        assertTrue(matcher.find());
        String uuid = matcher.group(1);

        //Retrieve
        assertEquals("0", httpClient.get(baseUrl() + "/uuid/UIDM/node/" + uuid, SC_OK));

        //(can't) Update
        response = httpClient.executeCypher(baseNeoUrl(), "MATCH (p:Person {name:'Luanne'}) SET p.uuid=new");

        System.out.println(response);

        response = httpClient.executeCypher(baseNeoUrl(), "MATCH (p:Person) RETURN p");

        matcher = UUID_PATTERN.matcher(response);
        assertTrue(matcher.find());
        assertEquals(uuid, matcher.group(1));

        //Delete
        httpClient.executeCypher(baseNeoUrl(), "MATCH (p:Person {name:'Luanne'}) DELETE p");
        httpClient.get(baseUrl() + "/uuid/node/" + uuid, SC_NOT_FOUND);
    }

    @Test
    public void testTimestampsWorkflow() {
        //Create & Assign
        httpClient.executeCypher(baseNeoUrl(), "CREATE (p:Person {name:'Luanne'})");

        String response = httpClient.executeCypher(baseNeoUrl(), "MATCH (p:Person) RETURN p");

        System.out.println(response);

        Matcher createdAtMatcher = CREATED_AT_PATTERN.matcher(response);
        assertTrue(createdAtMatcher.find());
        String createdAt = createdAtMatcher.group(1);

        Matcher updatedAtMatcher = UPDATED_AT_PATTERN.matcher(response);
        assertTrue(updatedAtMatcher.find());
        String updatedAt = updatedAtMatcher.group(1);

        assertEquals(createdAt, updatedAt);

        //Update
        httpClient.executeCypher(baseNeoUrl(), "MATCH (p:Person {name:'Luanne'}) SET p.name='Not Luanne'");

        response = httpClient.executeCypher(baseNeoUrl(), "MATCH (p:Person) RETURN p");

        System.out.println(response);

        createdAtMatcher = CREATED_AT_PATTERN.matcher(response);
        assertTrue(createdAtMatcher.find());
        String newCreatedAt = createdAtMatcher.group(1);

        assertEquals(createdAt, newCreatedAt);

        updatedAtMatcher = UPDATED_AT_PATTERN.matcher(response);
        assertTrue(updatedAtMatcher.find());
        String newUpdatedAt = updatedAtMatcher.group(1);

        assertNotEquals(updatedAt, newUpdatedAt);
    }

    @Test
    public void testWorkflowWithManuallyAssignedId() {
        //Create & Assign
        httpClient.executeCypher(baseNeoUrl(), "CREATE (p:Person {name:'Luanne', uuid:'123'})");

        String response = httpClient.executeCypher(baseNeoUrl(), "MATCH (p:Person) RETURN p");

        Matcher matcher = UUID_PATTERN.matcher(response);
        assertTrue(matcher.find());
        String uuid = matcher.group(1);

        //Retrieve
        assertEquals("0", httpClient.get(baseUrl() + "/uuid/UIDM/node/" + uuid, SC_OK));

        //(can't) Update
        response = httpClient.executeCypher(baseNeoUrl(), "MATCH (p:Person {name:'Luanne'}) SET p.uuid=new");

        System.out.println(response);

        response = httpClient.executeCypher(baseNeoUrl(), "MATCH (p:Person) RETURN p");

        matcher = UUID_PATTERN.matcher(response);
        assertTrue(matcher.find());
        assertEquals(uuid, matcher.group(1));

        //Delete
        httpClient.executeCypher(baseNeoUrl(), "MATCH (p:Person {name:'Luanne'}) DELETE p");
        httpClient.get(baseUrl() + "/uuid/node/" + uuid, SC_NOT_FOUND);
    }

    @Test
    public void testTimestampsWorkflowWithManuallyAssignedValue() {
        //Create & Assign
        httpClient.executeCypher(baseNeoUrl(), "CREATE (p:Person {name:'Luanne', createdAt:123})");

        String response = httpClient.executeCypher(baseNeoUrl(), "MATCH (p:Person) RETURN p");

        System.out.println(response);

        Matcher createdAtMatcher = CREATED_AT_PATTERN.matcher(response);
        assertTrue(createdAtMatcher.find());
        assertEquals("123", createdAtMatcher.group(1));
    }

    @Test
    public void shouldNotBeAbleToCreateTwoNodesWithTheSameUUID() {
        String response = httpClient.executeCypher(baseNeoUrl(), "CREATE (:Person {name:'Luanne', uuid:'123'}), (:Person {name:'Michal', uuid:'123'})");

        assertTrue(response.contains("Neo.ClientError.Transaction.TransactionHookFailed"));

        assertEquals("{\"results\":[{\"columns\":[\"p\"],\"data\":[]}],\"errors\":[]}", httpClient.executeCypher(baseNeoUrl(), "MATCH (p:Person) RETURN p"));
    }

    @Test
    public void shouldNotBeAbleToCreateTwoRelsWithTheSameUUID() {
        String response = httpClient.executeCypher(baseNeoUrl(), "CREATE (p1:Person {name:'Luanne'}), (p2:Person {name:'Michal'}), (p1)-[:FRIEND_OF {uuid:'123'}]->(p2), (p1)-[:COLLEAGUE_OF {uuid:'123'}]->(p2)");

        assertTrue(response.contains("Neo.ClientError.Transaction.TransactionHookFailed"));

        assertEquals("{\"results\":[{\"columns\":[\"p\"],\"data\":[]}],\"errors\":[]}", httpClient.executeCypher(baseNeoUrl(), "MATCH (p:Person) RETURN p"));
    }

    @Test
    public void shouldReturn404WhenUuidNotExists() {
        httpClient.get(baseUrl() + "/uuid/node/not-exists", SC_NOT_FOUND);
    }

    @Test
    public void shouldReturn404WhenModuleNotExists() {
        httpClient.get(baseUrl() + "/uuid/non-existing-module/node/not-exists", SC_NOT_FOUND);
    }
}

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.shaie.browze.resources;

import static com.google.common.base.Preconditions.*;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.commons.lang3.StringUtils;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.zookeeper.KeeperException.NoNodeException;
import org.apache.zookeeper.data.Stat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.annotation.Timed;
import com.shaie.browze.model.ZkNode;

@Path("/zoo")
@Produces(MediaType.APPLICATION_JSON)
public class ZooResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(ZooResource.class);

    private final CuratorFramework curatorFramework;

    public ZooResource(String zkHost) {
        checkNotNull(zkHost);

        LOGGER.info("Connecting to zkHost [{}]", zkHost);
        this.curatorFramework = CuratorFrameworkFactory.builder()
                .connectString(zkHost)
                .connectionTimeoutMs(30000)
                .retryPolicy(new ExponentialBackoffRetry(1000, 3))
                .build();
        curatorFramework.start();
        try {
            LOGGER.info("Blocking until connection with ZooKeeper is established");
            if (!curatorFramework.blockUntilConnected(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException(String.format(Locale.ROOT,
                        "Failed to establish connection with ZooKeeper at [%s] for 10 seconds", zkHost));
            }
            LOGGER.info("Started CuratorFramework");
        } catch (final InterruptedException e) {
            throw new IllegalStateException("Interrupted while waiting for connection with ZooKeeper", e);
        }
    }

    @Path("/{path:.*}")
    @GET
    @Timed
    public Response getNodeData(@PathParam("path") String path) throws Exception {
        final String zkPath = "/" + StringUtils.strip(path, "/");
        try {
            final Stat stat = new Stat();
            final byte[] data = curatorFramework.getData().storingStatIn(stat).forPath(zkPath);
            final List<String> children = curatorFramework.getChildren().forPath(zkPath);
            final ZkNode zkNode = new ZkNode(data, children);
            return Response.ok(zkNode).build();
        } catch (@SuppressWarnings("unused") final NoNodeException e) {
            return Response.status(Status.NOT_FOUND)
                    .type(MediaType.TEXT_PLAIN)
                    .entity("Path not found in ZooKeeper: " + zkPath)
                    .build();
        }
    }

}
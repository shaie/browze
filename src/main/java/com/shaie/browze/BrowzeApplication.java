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
package com.shaie.browze;

import com.shaie.browze.resources.ZooResource;
import com.shaie.browze.validation.GeneralExceptionMapper;

import io.dropwizard.Application;
import io.dropwizard.assets.AssetsBundle;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;

public class BrowzeApplication extends Application<BrowzeConfiguration> {

    private static final String APP_NAME = "browze";

    public static void main(String[] args) throws Exception {
        new BrowzeApplication().run(args);
    }

    @Override
    public String getName() {
        return APP_NAME;
    }

    @Override
    public void initialize(Bootstrap<BrowzeConfiguration> bootstrap) {
        bootstrap.addBundle(new AssetsBundle("/app", "/", "index.html", "app"));
    }

    @Override
    public void run(BrowzeConfiguration configuration, Environment environment) {
        final ZooResource resource = new ZooResource();
        environment.jersey().register(resource);

        final BrowzeHealthCheck healthCheck = new BrowzeHealthCheck();
        environment.healthChecks().register("healthcheck", healthCheck);

        environment.jersey().register(new GeneralExceptionMapper());
    }

}
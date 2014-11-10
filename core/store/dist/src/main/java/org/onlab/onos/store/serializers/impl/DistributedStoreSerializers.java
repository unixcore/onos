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
package org.onlab.onos.store.serializers.impl;

import org.onlab.onos.store.impl.MastershipBasedTimestamp;
import org.onlab.onos.store.impl.Timestamped;
import org.onlab.onos.store.impl.WallClockTimestamp;
import org.onlab.onos.store.serializers.KryoNamespaces;
import org.onlab.util.KryoNamespace;

public final class DistributedStoreSerializers {

    /**
     * KryoNamespace which can serialize ON.lab misc classes.
     */
    public static final KryoNamespace COMMON = KryoNamespace.newBuilder()
            .register(KryoNamespaces.API)
            .register(Timestamped.class)
            .register(MastershipBasedTimestamp.class, new MastershipBasedTimestampSerializer())
            .register(WallClockTimestamp.class)
            .build();

    // avoid instantiation
    private DistributedStoreSerializers() {}
}
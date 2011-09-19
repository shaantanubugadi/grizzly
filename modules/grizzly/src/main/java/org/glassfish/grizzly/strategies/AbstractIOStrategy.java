/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2011 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */
package org.glassfish.grizzly.strategies;

import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Context;
import org.glassfish.grizzly.IOEvent;
import org.glassfish.grizzly.IOEventProcessingHandler;
import org.glassfish.grizzly.IOStrategy;
import org.glassfish.grizzly.threadpool.ThreadPoolConfig;

import java.io.IOException;
import java.util.EnumSet;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.EmptyIOEventProcessingHandler;

import org.glassfish.grizzly.Transport;
import org.glassfish.grizzly.asyncqueue.AsyncQueue;

/**
 *
 * @author oleksiys
 */
public abstract class AbstractIOStrategy implements IOStrategy {

    private final static EnumSet<IOEvent> READ_WRITE_EVENT_SET =
            EnumSet.<IOEvent>of(IOEvent.READ, IOEvent.WRITE);

    private final static EnumSet<IOEvent> WORKER_THREAD_EVENT_SET =
            EnumSet.<IOEvent>of(IOEvent.READ, IOEvent.WRITE, IOEvent.CLOSED);
    
    protected final static IOEventProcessingHandler ENABLE_INTEREST_PROCESSING_HANDLER =
            new EnableInterestProcessingHandler();


    // ----------------------------- Methods from WorkerThreadPoolConfigProducer


    @Override
    public ThreadPoolConfig createDefaultWorkerPoolConfig(final Transport transport) {

        final ThreadPoolConfig config = ThreadPoolConfig.defaultConfig().copy();
        final int coresCount = Runtime.getRuntime().availableProcessors();
        config.setCorePoolSize(coresCount * 2);
        config.setMaxPoolSize(coresCount * 2);
        config.setMemoryManager(transport.getMemoryManager());
        return config;

    }

    // ------------------------------------------------------- Public Methods

    @Override
    public final boolean executeIoEvent(final Connection connection,
            final IOEvent ioEvent) throws IOException {
        return executeIoEvent(connection, ioEvent, true);
    }
    

    // ------------------------------------------------------- Protected Methods


    protected static boolean isReadWrite(final IOEvent ioEvent) {
        return READ_WRITE_EVENT_SET.contains(ioEvent);
    }

    protected static boolean isExecuteInWorkerThread(final IOEvent ioEvent) {
        return WORKER_THREAD_EVENT_SET.contains(ioEvent);
    }

    protected static Executor getWorkerThreadPool(final Connection c) {
        return c.getTransport().getWorkerThreadPool();
    }

    protected static void fireIOEvent(final Connection connection,
                                      final IOEvent ioEvent,
                                      final IOEventProcessingHandler ph,
                                      final Logger logger) {
        try {
            connection.getTransport().fireIOEvent(ioEvent, connection, ph);
        } catch (IOException e) {
            logger.log(Level.FINE, "Uncaught exception: ", e);
            try {
                connection.close().markForRecycle(true);
            } catch (IOException ee) {
                logger.log(Level.WARNING, "Exception occurred when " +
                        "closing the connection: ", ee);
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Uncaught exception: ", e);
            try {
                connection.close().markForRecycle(true);
            } catch (IOException ee) {
                logger.log(Level.WARNING, "Exception occurred when " +
                        "closing the connection: ", ee);
            }
        }

    }


    // ---------------------------------------------------------- Nested Classes


    private final static class EnableInterestProcessingHandler
            extends EmptyIOEventProcessingHandler {
        
        @Override
        public void onReregister(final Context context) throws IOException {
            onComplete(context, null);
        }

        @Override
        public void onComplete(final Context context, final Object data) throws IOException {
            final IOEvent ioEvent = context.getIoEvent();
            final Connection connection = context.getConnection();
            
            if (AsyncQueue.EXPECTING_MORE_OPTION.equals(data)) {
                connection.simulateIOEvent(ioEvent);
            } else {
                connection.enableIOEvent(ioEvent);
            }
        }
    }
}

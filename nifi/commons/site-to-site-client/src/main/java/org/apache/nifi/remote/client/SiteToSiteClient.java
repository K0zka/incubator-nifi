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
package org.apache.nifi.remote.client;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;

import org.apache.nifi.events.EventReporter;
import org.apache.nifi.remote.Transaction;
import org.apache.nifi.remote.TransferDirection;
import org.apache.nifi.remote.client.socket.SocketClient;

/**
 * <p>
 * The SiteToSiteClient provides a mechanism for sending data to a remote instance of NiFi
 * (or NiFi cluster) and retrieving data from a remote instance of NiFi (or NiFi cluster).
 * </p>
 * 
 * <p>
 * When configuring the client via the {@link SiteToSiteClient.Builder}, the Builder must
 * be provided the URL of the remote NiFi instance. If the URL points to a standalone instance
 * of NiFi, all interaction will take place with that instance of NiFi. However, if the URL 
 * points to the NiFi Cluster Manager of a cluster, the client will automatically handle load
 * balancing the transactions across the different nodes in the cluster.
 * </p>
 * 
 * <p>
 * The SiteToSiteClient provides a {@link Transaction} through which all interaction with the
 * remote instance takes place. After data has been exchanged or it is determined that no data
 * is available, the Transaction can then be canceled (via the {@link Transaction#cancel(String)}
 * method) or can be completed (via the {@link Transaction#complete(boolean)} method).
 * </p>
 * 
 * <p>
 * An instance of SiteToSiteClient can be obtained by constructing a new instance of the 
 * {@link SiteToSiteClient.Builder} class, calling the appropriate methods to configured the
 * client as desired, and then calling the {@link SiteToSiteClient.Builder#build() build()} method.
 * </p>
 *
 * <p>
 * The SiteToSiteClient itself is immutable once constructed and is thread-safe. Many threads can
 * share access to the same client. However, the {@link Transaction} that is created by the client
 * is not thread safe and should not be shared among threads.
 * </p>
 */
public interface SiteToSiteClient extends Closeable {

	/**
	 * Creates a new Transaction that can be used to either send data to a remote NiFi instance
	 * or receive data from a remote NiFi instance, depending on the value passed for the {@code direction} argument.
	 * 
	 * 
	 * @param direction specifies which direction the data should be transferred. A value of {@link TransferDirection#SEND}
	 * indicates that this Transaction will send data to the remote instance; a value of {@link TransferDirection#RECEIVE} indicates
	 * that this Transaction will be used to receive data from the remote instance.
	 * 
	 * @return
	 * @throws IOException
	 */
	Transaction createTransaction(TransferDirection direction) throws IOException;
	
	
	/**
	 * <p>
	 * The Builder is the mechanism by which all configuration is passed to the SiteToSiteClient.
	 * Once constructed, the SiteToSiteClient cannot be reconfigured (i.e., it is immutable). If
	 * a change in configuration should be desired, the client should be {@link Closeable#close() closed}
	 * and a new client created. 
	 * </p>
	 */
	public static class Builder {
		private String url;
		private long timeoutNanos = TimeUnit.SECONDS.toNanos(30);
		private long penalizationNanos = TimeUnit.SECONDS.toNanos(3);
		private SSLContext sslContext;
		private EventReporter eventReporter;
		private File peerPersistenceFile;
		private boolean useCompression;
		private String portName;
		private String portIdentifier;

		/**
		 * Specifies the URL of the remote NiFi instance. If this URL points to the Cluster Manager of
		 * a NiFi cluster, data transfer to and from nodes will be automatically load balanced across
		 * the different nodes.
		 * 
		 * @param url
		 * @return
		 */
		public Builder url(final String url) {
			this.url = url;
			return this;
		}
		
		/**
		 * Specifies the communications timeouts to use when interacting with the remote instances. The
		 * default value is 30 seconds.
		 * 
		 * @param timeout
		 * @param unit
		 * @return
		 */
		public Builder timeout(final long timeout, final TimeUnit unit) {
			this.timeoutNanos = unit.toNanos(timeout);
			return this;
		}
		
		/**
		 * If there is a problem communicating with a node (i.e., any node in the remote NiFi cluster
		 * or the remote instance of NiFi if it is standalone), specifies how long the client should
		 * wait before attempting to communicate with that node again. While a particular node is penalized,
		 * all other nodes in the remote cluster (if any) will still be available for communication.
		 * The default value is 3 seconds.
		 * 
		 * @param period
		 * @param unit
		 * @return
		 */
		public Builder nodePenalizationPeriod(final long period, final TimeUnit unit) {
			this.penalizationNanos = unit.toNanos(period);
			return this;
		}
		
		/**
		 * Specifies the SSL Context to use when communicating with the remote NiFi instance(s). If not
		 * specified, communications will not be secure. The remote instance of NiFi always determines
		 * whether or not Site-to-Site communications are secure (i.e., the client will always use
		 * secure or non-secure communications, depending on what the server dictates).
		 * 
		 * @param sslContext
		 * @return
		 */
		public Builder sslContext(final SSLContext sslContext) {
			this.sslContext = sslContext;
			return this;
		}
		
		
		/**
		 * Provides an EventReporter that can be used by the client in order to report any events that
		 * could be of interest when communicating with the remote instance. The EventReporter provided
		 * must be threadsafe.
		 * 
		 * @param eventReporter
		 * @return
		 */
		public Builder eventReporter(final EventReporter eventReporter) {
			this.eventReporter = eventReporter;
			return this;
		}
		
		
		/**
		 * Specifies a file that the client can write to in order to persist the list of nodes in the
		 * remote cluster and recover the list of nodes upon restart. This allows the client to function
		 * if the remote Cluster Manager is unavailable, even after a restart of the client software.
		 * If not specified, the list of nodes will not be persisted and a failure of the Cluster Manager
		 * will result in not being able to communicate with the remote instance if a new client
		 * is created. 
		 * 
		 * @param peerPersistenceFile
		 * @return
		 */
		public Builder peerPersistenceFile(final File peerPersistenceFile) {
			this.peerPersistenceFile = peerPersistenceFile;
			return this;
		}
		
		/**
		 * Specifies whether or not data should be compressed before being transferred to or from the
		 * remote instance.
		 * 
		 * @param compress
		 * @return
		 */
		public Builder useCompression(final boolean compress) {
			this.useCompression = compress;
			return this;
		}
		
		/**
		 * Specifies the name of the port to communicate with. Either the port name or the port identifier
		 * must be specified.
		 * 
		 * @param portName
		 * @return
		 */
		public Builder portName(final String portName) {
			this.portName = portName;
			return this;
		}
		
		/**
		 * Specifies the unique identifier of the port to communicate with. If it is known, this is preferred over providing
		 * the port name, as the port name may change.
		 * 
		 * @param portIdentifier
		 * @return
		 */
		public Builder portIdentifier(final String portIdentifier) {
			this.portIdentifier = portIdentifier;
			return this;
		}
		
		/**
		 * Builds a new SiteToSiteClient that can be used to send and receive data with remote instances of NiFi
		 * @return
		 */
		public SiteToSiteClient build() {
			if ( url == null ) {
				throw new IllegalStateException("Must specify URL to build Site-to-Site client");
			}
			
			if ( portName == null && portIdentifier == null ) {
				throw new IllegalStateException("Must specify either Port Name or Port Identifier to builder Site-to-Site client");
			}
			
			return new SocketClient(this);
		}

		public String getUrl() {
			return url;
		}

		public long getTimeoutNanos() {
			return timeoutNanos;
		}

		public long getPenalizationNanos() {
			return penalizationNanos;
		}

		public SSLContext getSslContext() {
			return sslContext;
		}

		public EventReporter getEventReporter() {
			return eventReporter;
		}

		public File getPeerPersistenceFile() {
			return peerPersistenceFile;
		}

		public boolean isUseCompression() {
			return useCompression;
		}

		public String getPortName() {
			return portName;
		}

		public String getPortIdentifier() {
			return portIdentifier;
		}
	}
}

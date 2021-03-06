/*
 * OpenCredo-Esper - simplifies adopting Esper in Java applications. 
 * Copyright (C) 2010  OpenCredo Ltd.
 * 
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package org.opencredo.esper;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;

import com.espertech.esper.client.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;

import org.springframework.util.Assert;

/**
 * The main workhorse of Esper. The template is configured with a set of
 * statements that query the flow of events. Each statement is then associated
 * with a number of listeners who will be notified when there is a result from
 * the statement.
 * <p/>
 * Once setup the template is then used to inform esper of any events of
 * interest by calling sendEvent(Object).
 * 
 * @author Russ Miles (russ.miles@opencredo.com)
 * @author Jonas Partner (jonas.partner@opencredo.com)
 * @author Aleksa Vukotic (aleksa.vukotic@opencredo.com)
 */
public class EsperTemplate implements EsperTemplateOperations {
    private final static Logger LOG = LoggerFactory.getLogger(EsperTemplate.class);

    private EPServiceProvider epServiceProvider;
    private EPRuntime epRuntime;
    private String name;
    private Set<EsperStatement> statements = new LinkedHashSet<EsperStatement>();
    private Resource configuration;
    private UnmatchedListener unmatchedListener;
    private volatile boolean initialised = false;

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return this.name;
    }

    public void setStatements(Set<EsperStatement> statements) {
        this.statements = statements;
    }

    public void setConfiguration(Resource configuration) {
        this.configuration = configuration;
    }

    public void setUnmatchedListener(UnmatchedListener unmatchedListener) {
        this.unmatchedListener = unmatchedListener;
    }

    public EPRuntime getEsperNativeRuntime() {
        return epRuntime;
    }

    public Set<EsperStatement> getStatements() {
        return this.statements;
    }

    public synchronized void addStatement(EsperStatement statement) {
        statements.add(statement);
        if (this.initialised) {
            EPStatement epStatement = epServiceProvider.getEPAdministrator().createEPL(statement.getEPL());
            statement.setEPStatement(epStatement);
        }
    }

    public synchronized void destroyStatement(String statementId) {
        EsperStatement statement = findStatementById(statementId);
        statement.destroy();
        Assert.isTrue(statement.getState().equals(EPStatementState.DESTROYED));
        this.statements.remove(statement);

    }
    public synchronized void startStatement(String statementId) {
        EsperStatement statement = findStatementById(statementId);
        statement.start();
    }
    public synchronized void stopStatement(String statementId) {
        EsperStatement statement = findStatementById(statementId);
        statement.stop();
    }

    private EsperStatement findStatementById(String id){
        for(EsperStatement statement : this.statements){
            if(statement.getId().equals(id)){
                return statement;
            }
        }
        throw new IllegalArgumentException("EsperStatement with id " + id + "does not exist");
    }

    public void sendEvent(Object event) throws InvalidEsperConfigurationException {
        LOG.debug("Sending event to Esper");
        if (epRuntime != null) {
            epRuntime.sendEvent(event);
        } else {
            LOG.error("Attempted to send message with null Esper Runtime.");
            throw new InvalidEsperConfigurationException(
                    "Esper Runtime is null. Have you initialized the template before you attempt to send an event?");
        }
        LOG.debug("Sent event to Esper");
    }

    public synchronized void initialize() throws InvalidEsperConfigurationException {
        if (this.initialised) {
            throw new InvalidEsperConfigurationException("EsperTemplate should only be initialised once");
        }
        this.initialised = true;
        LOG.debug("Initializing esper template");
        try {
            configureEPServiceProvider();
            epRuntime = epServiceProvider.getEPRuntime();
            if (this.unmatchedListener != null) {
                epRuntime.setUnmatchedListener(unmatchedListener);
            }
            setupEPStatements();
        } catch (Exception e) {
            LOG.error("An exception occured when attempting to initialize the esper template", e);
            throw new InvalidEsperConfigurationException(e.getMessage(), e);
        }
        LOG.debug("Finished initializing esper template");
    }

    public void cleanup() {
        epServiceProvider.destroy();
    }

    /**
     * Add the appropriate statements to the esper runtime.
     */
    private void setupEPStatements() {
        for (EsperStatement statement : statements) {
            EPStatement epStatement = epServiceProvider.getEPAdministrator().createEPL(statement.getEPL());
            statement.setEPStatement(epStatement);
        }
    }

    /**
     * Configure the Esper Service Provider to create the appropriate Esper
     * Runtime.
     * 
     * @throws IOException
     * @throws EPException
     */
    private void configureEPServiceProvider() throws EPException, IOException {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Configuring the Esper Service Provider with name: " + name);
        }
        if (this.configuration != null && this.configuration.exists()) {
            Configuration esperConfiguration = new Configuration();
            esperConfiguration = esperConfiguration.configure(this.configuration.getFile());
            epServiceProvider = EPServiceProviderManager.getProvider(name, esperConfiguration);
            LOG.info("Esper configured with a user-provided configuration", esperConfiguration);
        } else {
            epServiceProvider = EPServiceProviderManager.getProvider(name);
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("Completed configuring the Esper Service Provider with name: " + name);
        }
    }
}

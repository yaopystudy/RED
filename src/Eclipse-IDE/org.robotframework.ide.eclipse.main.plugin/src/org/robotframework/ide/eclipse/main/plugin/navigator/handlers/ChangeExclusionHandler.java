/*
 * Copyright 2015-2017 Nokia Solutions and Networks
 * Licensed under the Apache License, Version 2.0,
 * see license.txt file for details.
 */
package org.robotframework.ide.eclipse.main.plugin.navigator.handlers;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.e4.core.services.events.IEventBroker;
import org.eclipse.ui.IDecoratorManager;
import org.eclipse.ui.PlatformUI;
import org.rf.ide.core.project.RobotProjectConfig;
import org.robotframework.ide.eclipse.main.plugin.RedPlugin;
import org.robotframework.ide.eclipse.main.plugin.model.RobotProject;
import org.robotframework.ide.eclipse.main.plugin.navigator.RobotValidationExcludedDecorator;
import org.robotframework.ide.eclipse.main.plugin.project.RedEclipseProjectConfigWriter;
import org.robotframework.ide.eclipse.main.plugin.project.RedProjectConfigEventData;
import org.robotframework.ide.eclipse.main.plugin.project.RobotProjectConfigEvents;
import org.robotframework.red.swt.SwtThread;

/**
 * @author Michal Anglart
 */
abstract class ChangeExclusionHandler {

    protected boolean changeExclusion(final IEventBroker eventBroker, final List<IResource> selectedResources) {
        boolean wasChanged = false;
        for (final Entry<RobotProject, List<IPath>> entry : groupByProject(selectedResources).entrySet()) {
            wasChanged |= changeExclusion(entry.getKey(), entry.getValue(), eventBroker);
        }

        if (wasChanged) {
            SwtThread.asyncExec(() -> {
                final IDecoratorManager manager = PlatformUI.getWorkbench().getDecoratorManager();
                manager.update(RobotValidationExcludedDecorator.ID);
            });
        }

        return wasChanged;
    }

    private Map<RobotProject, List<IPath>> groupByProject(final List<IResource> selectedResources) {
        final Function<IResource, RobotProject> projectClassifier = resource -> RedPlugin.getModelManager()
                .createProject(resource.getProject());
        return selectedResources.stream().collect(Collectors.groupingBy(projectClassifier,
                Collectors.mapping(IResource::getProjectRelativePath, Collectors.toList())));
    }

    private boolean changeExclusion(final RobotProject robotProject, final Collection<IPath> toChange,
            final IEventBroker eventBroker) {
        final Optional<RobotProjectConfig> openedConfig = robotProject.getOpenedProjectConfig();
        final RobotProjectConfig config = openedConfig.orElseGet(robotProject::getRobotProjectConfig);

        boolean wasChanged = false;
        for (final IPath pathToChange : toChange) {
            wasChanged |= changeExclusion(config, pathToChange);
        }

        if (wasChanged) {
            if (!openedConfig.isPresent()) {
                new RedEclipseProjectConfigWriter().writeConfiguration(config, robotProject);
            }
            try {
                robotProject.getProject().refreshLocal(IResource.DEPTH_INFINITE, null);
            } catch (final CoreException e) {
                // nothing to do
            }
            fireEvents(eventBroker, robotProject.getProject(), toChange);
        }
        return wasChanged;
    }

    private void fireEvents(final IEventBroker eventBroker, final IProject project, final Collection<IPath> toChange) {
        final RedProjectConfigEventData<Collection<IPath>> eventData = new RedProjectConfigEventData<>(
                project.getFile(RobotProjectConfig.FILENAME), toChange);
        eventBroker.send(RobotProjectConfigEvents.ROBOT_CONFIG_VALIDATION_EXCLUSIONS_STRUCTURE_CHANGED, eventData);
    }

    protected abstract boolean changeExclusion(RobotProjectConfig config, IPath pathToChange);

}

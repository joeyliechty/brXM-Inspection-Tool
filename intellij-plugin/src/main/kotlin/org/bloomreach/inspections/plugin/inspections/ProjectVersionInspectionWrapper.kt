package org.bloomreach.inspections.plugin.inspections

import org.bloomreach.inspections.core.engine.Inspection
import org.bloomreach.inspections.core.inspections.deployment.ProjectVersionInspection

/**
 * IntelliJ wrapper for ProjectVersionInspection
 */
class ProjectVersionInspectionWrapper : BrxmInspectionBase() {
    override val coreInspection: Inspection = ProjectVersionInspection()
}

package com.fdu.jira.plugin.gadget.timesheet;

import net.jcip.annotations.Immutable;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * JAXB representation of a group of projects.
 */
@Immutable
@XmlRootElement
public class TimeSheetRepresentation {

    @XmlElement
    private String html;

    @XmlElement
    private String projectOrFilterName;

    private TimeSheetRepresentation() {
        // for JAXB
    }

    public TimeSheetRepresentation(String html, String projectOrFilterName) {
        this.html = html;
        this.projectOrFilterName = projectOrFilterName;
    }
}

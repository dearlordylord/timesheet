package com.fdu.jira.plugin.report.timesheet;

import java.util.Comparator;

import com.atlassian.jira.issue.Issue;

public class IssueProjectComparator<T extends Issue> implements Comparator<T> {
    public int compare(T o1, T o2) {
        if(o1 == null && o2 == null)
            return 0;
        if(o1 == null)
            return -1;
        if(o2 == null)
            return 1;
        else
            return ((Issue)o1).getKey().compareTo(((Issue)o2).getKey());
    }
}

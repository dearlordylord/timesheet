package com.fdu.jira.util;

import java.util.Comparator;

public class MyFullNameComparator<T extends MyUser> implements Comparator<T> {
    public int compare(T o1, T o2) {
        if (o1 == null && o2 == null)
            return 0;
        else if (o2 == null) // any value is less than null
            return -1;
        else if (o1 == null) // null is greater than any value
            return 1;

        String fullName1 = o1.getFullName();
        String fullName2 = o2.getFullName();

        if (fullName1 == null) {
            return -1;
        } else if (fullName2 == null) {
            return 1;
        } else {
            return fullName1.toLowerCase().compareTo(fullName2.toLowerCase()); //do case insensitive sorting
        }
    }
}

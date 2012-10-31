package com.fdu.jira.util;

import com.atlassian.core.util.DateUtils;
import com.atlassian.crowd.embedded.api.User;
import com.atlassian.jira.ComponentManager;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.config.properties.ApplicationProperties;
import com.atlassian.jira.datetime.DateTimeFormatter;
import com.atlassian.jira.issue.CustomFieldManager;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.customfields.manager.OptionsManager;
import com.atlassian.jira.issue.customfields.option.Option;
import com.atlassian.jira.issue.customfields.option.Options;
import com.atlassian.jira.issue.customfields.statistics.CustomFieldStattable;
import com.atlassian.jira.issue.fields.AffectedVersionsSystemField;
import com.atlassian.jira.issue.fields.ComponentsSystemField;
import com.atlassian.jira.issue.fields.CustomField;
import com.atlassian.jira.issue.fields.Field;
import com.atlassian.jira.issue.fields.FieldManager;
import com.atlassian.jira.issue.fields.FixVersionsSystemField;
import com.atlassian.jira.issue.fields.IssueTypeSystemField;
import com.atlassian.jira.issue.fields.LabelsSystemField;
import com.atlassian.jira.issue.link.IssueLinkManager;
import com.atlassian.jira.issue.link.IssueLinkType;
import com.atlassian.jira.issue.link.LinkCollection;
import com.atlassian.jira.issue.statistics.StatisticsMapper;
import com.atlassian.jira.ofbiz.OfBizValueWrapper;
import com.atlassian.jira.web.bean.I18nBean;
import org.apache.log4j.Logger;

import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.servlet.http.HttpServletRequest;

public class TextUtil {

    private final static Logger log = Logger.getLogger(TextUtil.class);

	private long secondsPerDay;
	private long secondsPerWeek;
	private NumberFormat decimalFormat;
	private NumberFormat percentFormat;
	private Pattern servletUrlPattern;
	private DateFormat dateFormat1;
	private DateFormat dateFormat2;
	private Boolean prettyDuration = false;

	/**
	 * Instantiate TextUtil object
	 * @param i18nBean
	 */
	public TextUtil(I18nBean i18nBean, TimeZone timezone) {
            ApplicationProperties ap = ComponentManager.getComponentInstanceOfType(ApplicationProperties.class);
		secondsPerDay = new Float(Float.valueOf(ap.getDefaultBackedString("jira.timetracking.hours.per.day")) * 3600).longValue();
		secondsPerWeek = new Float(Float.valueOf(ap.getDefaultBackedString("jira.timetracking.days.per.week")) * secondsPerDay).longValue();
		decimalFormat = NumberFormat.getInstance(i18nBean.getLocale());
		percentFormat = NumberFormat.getPercentInstance(i18nBean.getLocale());
		servletUrlPattern = Pattern.compile("^(.+?)://(.+?)/(.+)$");
                String formatDay1 = ap.getDefaultBackedString("jira.timesheet.plugin.dayFormat1");
                if (formatDay1 == null) {
                    formatDay1 = "E";
                }
		String formatDay2 = ap.getDefaultBackedString("jira.timesheet.plugin.dayFormat2");
		if (formatDay2 == null) {
		    formatDay2 = "d/MMM";
		}
		// FIXME: match the user's specified time zone in JIRA,
		// use com.atlassian.jira.datetime.DateTimeFormatter 
	        dateFormat1 = new SimpleDateFormat(formatDay1,i18nBean.getLocale());
	        dateFormat1.setTimeZone(timezone);
	        dateFormat2 = new SimpleDateFormat(formatDay2,i18nBean.getLocale());
                dateFormat2.setTimeZone(timezone);

                String decimalSeparator = ap.getDefaultBackedString("jira.timesheet.plugin.decimalSeparator");
                if (decimalSeparator != null && decimalFormat instanceof DecimalFormat) {
                    DecimalFormatSymbols dfs = ((DecimalFormat)decimalFormat).getDecimalFormatSymbols();
                    dfs.setDecimalSeparator(decimalSeparator.charAt(0));
                    ((DecimalFormat)decimalFormat).setDecimalFormatSymbols(dfs);
                }

                String prettyDuration = ap.getDefaultBackedString("jira.timesheet.plugin.prettyDuration");
                if (prettyDuration != null) {
                    this.prettyDuration = new Boolean(prettyDuration);
                }
	}

	public String formatDay(Date date) {
	    return dateFormat1.format(date) + "<br/>" + dateFormat2.format(date);
	}
	
	/**
	 * Format duration value
	 * @param value
	 * @return pretty formatted value, using Jira settings for hours in day, and days in week.
	 */
    public String getPrettyDuration(long value) {
        return DateUtils.getDurationStringSeconds(value, secondsPerDay, secondsPerWeek);
    }
    
	/**
	 * Format duration value in hours
	 * @param value
	 * @return value
	 */
    public String getPrettyHours(long value) {
        return prettyDuration ? getPrettyDuration(value) : getHours(value) + "h";
    }

	/**
	 * Format duration value in hours
	 * @param value
	 * @return pretty formatted value
	 */
    public String getHours(long value)
    {
    	return decimalFormat.format(((float)value) / 60 / 60);
    }
    
    /**
     * Expand relative url to absolute path
     */
    public String expandUrl(HttpServletRequest req, String url) {
    	String path = req.getRequestURL().toString();
    	Matcher m = servletUrlPattern.matcher(path);
    	if (m.matches()) {
    		return m.group(1) + "://" + m.group(2) + req.getContextPath() + url;
    	} else {
    		return url;
    	}
    }

    /** 
     * Convert to percents.
     * 
     * @param value value
     * @param hundred hundreds of value
     * @return percetns
     */
    public String getPercents(long value, long hundred) {
        if (hundred == 0L) {
            return "&nbsp;";
        }
        float percents = ((float)value) * 100 / hundred;
        return percentFormat.format(percents);
    }

    /**
     * Get issue field value by field id for the issue.
     *
     * @param groupByFieldID
     * @param issue
     * @param outlookDate
     * @return String value concatenated for multi-select or null.
     */
    public static String getFieldValue(String groupByFieldID, Issue issue,
            DateTimeFormatter  dateTimeFormatter) {
        Field groupByField = ComponentManager.getComponentInstanceOfType(
                FieldManager.class).getField(groupByFieldID);

        // Set field value
        String fieldValue = null;
        if (groupByField instanceof CustomField) {
            CustomField cf = (CustomField) groupByField;
            Object value = issue
                    .getCustomFieldValue(cf);
           if ("Цель".equals(cf.getName())) {
                if (value == null && issue.getIssueTypeObject().isSubTask()) {
                    value = issue.getParentObject().getCustomFieldValue((CustomField) groupByField);
                } else {
                    boolean noValueValue = false;
                    if (value != null && value instanceof Collection) {
                        Collection vc = (Collection) value;
                        if (vc.size() == 1) {
                            noValueValue = "Нет".equals(vc.iterator().next().toString());
                        }
                    }
                    if ((value == null || noValueValue) && "Bug".equalsIgnoreCase(issue.getIssueTypeObject().getName())) {
                        IssueLinkManager issueLinkManager = ComponentAccessor.getIssueLinkManager();
                        LinkCollection linkCollection = issueLinkManager.getLinkCollectionOverrideSecurity(issue);
                        List<Issue> linksI = linkCollection.getInwardIssues("Relates");
                        List<Issue> linksO = new ArrayList<Issue>();
                        List<Issue> addLinkO1 = linkCollection.getOutwardIssues("Иерархия");
                        if (addLinkO1 != null) linksO.addAll(addLinkO1);
                        List<Issue> addLinkO2 = linkCollection.getOutwardIssues("Relates");
                        if (addLinkO2 != null) linksO.addAll(addLinkO2);
                        Set<Issue> links = new HashSet<Issue>();
                        if (linksI != null) links.addAll(linksI);
                        if (linksO != null) links.addAll(linksO);
                        List<Issue> filteredLinks = new ArrayList<Issue>();
                        for (Issue i : links) {
                            if (!"Bug".equals(i.getIssueTypeObject().getName())) {
                                filteredLinks.add(i);
                            }
                        }
                        if (filteredLinks.size() == 1) {
                            Issue relation = filteredLinks.get(0);
                            value = relation.getCustomFieldValue(cf);
                        } else if (filteredLinks.size() > 1) {
                            List vals = new ArrayList();
                            for (Issue i : links) {
                                Object val = i.getCustomFieldValue(cf);
                                if (val != null) {
                                    if (val instanceof Collection) {
                                        vals.addAll((Collection) val);
                                    } else {
                                        vals.add(val);
                                    }
                                }
                            }
                            if (vals.size() > 0) {
                                if (vals.size() > 1) {
                                    OptionsManager optionsManager = ComponentAccessor.getOptionsManager();
                                    final Options options = optionsManager.getOptions(cf.getRelevantConfig(issue));
                                    Collections.sort(vals, new Comparator() {
                                        public int compare(Object v1, Object v2) {
                                            Option option1 = options.getOptionForValue((v1==null?"":v1.toString()), null);
                                            Option option2 = options.getOptionForValue((v2==null?"":v2.toString()), null);
                                            if (option1 == null && option2 == null) return 0;
                                            else if (option1 == null && option2 != null) return -1;
                                            else if (option1 != null && option2 == null) return 1;
                                            else {
                                                return option2.getSequence().compareTo(option1.getSequence());
                                            }
                                        }
                                    });
                                }
                                value = vals;
                            }
                        }
                    }
                }
            }

            if (value instanceof ArrayList) {
                ArrayList al = (ArrayList) value;
                for (Object o : al) {
                    log.warn("issue : " + issue.getKey() + " type : " + o.getClass().getName());
                }
            }


            if (value != null) {
                if (groupByField instanceof CustomFieldStattable) {
                        StatisticsMapper sm = ((CustomFieldStattable)
                                groupByField).getStatisticsMapper((CustomField) groupByField);
                        fieldValue = sm.getValueFromLuceneField(value.toString()).toString();
                    }
                if (value instanceof List) {
                    fieldValue = getMultiValue((List) value);
                } else if (value instanceof Date ) {
                    fieldValue = dateTimeFormatter.format((Date) value);
                } else if (value instanceof User){
                    fieldValue = ((User) value).getDisplayName();
                } else {
                    fieldValue = value.toString();
                }
            }
        } else if (groupByField instanceof ComponentsSystemField) {
            /*
               * Implementation to handle GroupBy Component. Issue TIME-54.
               * Caveat: When there are multiple components assigned to one
               * issue, the component names are concatenated and the grouping
               * is done by the concatenated string. The issue isn't
               * counted/grouped for each component.
               */
            fieldValue = getMultiValue(issue.getComponents());
        } else if (groupByField instanceof AffectedVersionsSystemField) {
            fieldValue = getMultiValue(issue.getAffectedVersions());
        } else if (groupByField instanceof FixVersionsSystemField) {
            fieldValue = getMultiValue(issue.getFixVersions());
        } else if (groupByField instanceof IssueTypeSystemField) {
            fieldValue = issue.getIssueTypeObject().getNameTranslation();
        } else if (groupByField instanceof LabelsSystemField) {
            fieldValue = getMultiValue(issue.getLabels()); 
        } else {
            // TODO Couldn't find an easy way to get each fields value as
            // string. Workaround.
            try {
                fieldValue = (String) issue.getString(groupByFieldID);
            } catch (RuntimeException e) {
                fieldValue = "FieldTypeValueNotApplicableForGrouping";
            }
        }

        // need a string as reference element in map for grouping
        if (fieldValue == null || fieldValue.trim().length() == 0) {
            fieldValue = "NoValueForFieldOnIssue";
        }

        return fieldValue;        
    }

    private static String getMultiValue(Collection<? extends Object> values) {
        StringBuffer fieldValue = new StringBuffer();
        for (Iterator<? extends Object> i = values.iterator(); i.hasNext();) {
            Object o = i.next();
            String value;
            if (o instanceof Map) {
                Map<String, Object> map= (Map<String, Object>) o;
                // do not check if (map.containsKey("name")) intentionally
                // for better diagnosability
                value = (String) map.get("name");
            } else if (o instanceof OfBizValueWrapper) {
                OfBizValueWrapper map= (OfBizValueWrapper) o;
                value = map.getString("name");
            } else if (o instanceof User){
                value = ((User) o).getDisplayName();
            } else {
                value = o.toString();
            }
            if (fieldValue.length() != 0) {
                fieldValue.append(", ");
            }
            fieldValue.append(value);
        }
        return fieldValue.toString();
    }

    public static String getUnquotedString(String s) {
        StringBuffer r = new StringBuffer(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\'' || c == '{') {
                r.append("'");
            }
            r.append(c);
        }
        return r.toString();
    }

    public static String getFieldName(String fieldID) {
        FieldManager fieldManager = 
            (FieldManager) ComponentManager.getComponentInstanceOfType(FieldManager.class);
        Field groupByField = fieldManager.getField(fieldID);
        return groupByField.getName();
    }
}

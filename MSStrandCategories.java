/*
 * ====================================================================
 *
 * Follett School Solutions
 *
 * Copyright (c) Follett School Solutions
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, is not permitted without express written agreement
 * from Follett School Solutions.
 *
 * ====================================================================
 */
package com.x2dev.reports.ma.innovation;

import static com.follett.fsc.core.k12.beans.SystemPreferenceDefinition.STUDENT_ACTIVE_CODE;
import static com.follett.fsc.core.k12.business.ModelProperty.PATH_DELIMITER;
import static com.x2dev.sis.model.beans.SisPreferenceConstants.GRADEBOOK_AVERAGE_MODE;
import static com.x2dev.sis.model.beans.SisPreferenceConstants.GRADES_AVERAGE_DECIMALS;
import com.follett.fsc.core.framework.persistence.SubQuery;
import com.follett.fsc.core.k12.beans.QueryIterator;
import com.follett.fsc.core.k12.beans.SystemPreference;
import com.follett.fsc.core.k12.beans.X2BaseBean;
import com.follett.fsc.core.k12.business.PreferenceManager;
import com.follett.fsc.core.k12.business.PreferenceSet;
import com.follett.fsc.core.k12.tools.reports.ReportDataGrid;
import com.follett.fsc.core.k12.tools.reports.ReportJavaSourceNet;
import com.follett.fsc.core.k12.tools.reports.ReportUtils;
import com.follett.fsc.core.k12.web.UserDataContainer;
import com.x2dev.sis.model.beans.*;
import com.x2dev.sis.model.business.GradesManager;
import com.x2dev.sis.model.business.gradebook.AverageCalculatorFactory;
import com.x2dev.sis.model.business.gradebook.CategoryAverageCalculator;
import com.x2dev.sis.model.business.gradebook.GradebookManager;
import com.x2dev.sis.model.business.gradebook.TermAverageCalculator;
import com.x2dev.utils.CollectionUtils;
import com.x2dev.utils.KeyValuePair;
import com.x2dev.utils.StringUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import net.sf.jasperreports.engine.JRDataSource;
import org.apache.ojb.broker.query.Criteria;
import org.apache.ojb.broker.query.QueryByCriteria;

/**
 * Data source for the "Danger of Failing" report.
 *
 * Client wants a report that shows what classes a student is in danger of
 * failing based on a
 * user-determined
 * threshold. For classes where a student is at or below the threshold the
 * report also shows what
 * categories
 * the student is struggling in.
 *
 * @author Follett School Solutions
 */
public class DangerOfFailingCategoryAveragesData2 extends ReportJavaSourceNet {

  private static final String INPUT_SEPARATE_PAGES = "useSeparatePages";
  /**
   * String constants
   */
  private static final String NO_BREAK = "no break";
  private static final String FORMAT_ERROR = "Number Format Error in initialize method. "
      + "M_minAverage set to 0.0\n";
  private static final String GRADEBOOK_AVERAGES_GRADESCALE = "sys.gradebook.averageGradeScale";
  private static final String[] PREFERENCE_KEYS = {
      GRADES_AVERAGE_DECIMALS, GRADEBOOK_AVERAGE_MODE, GRADEBOOK_AVERAGES_GRADESCALE
  };
  private static final String HOMEROOM_KEY = "Homeroom";
  private static final String LUNCH_KEY = "Lunch";

  /**
   * Grid columns
   */
  private static final String GRID_BREAK = "break";
  private static final String GRID_CATEGORY = "category";
  private static final String GRID_ITEM_COUNT = "itemCount";
  private static final String GRID_LETTER_GRADE = "letterGrade";
  private static final String GRID_SECTION = "section";
  private static final String GRID_SEPARATE = "separate";
  private static final String GRID_SIZE = "setSize";
  private static final String GRID_STUDENT = "student";
  private static final String GRID_TEACHER = "teacher";

  /**
   * User inputs
   */
  private static final String INPUT_ACTIVE_ONLY = "activeOnly";
  private static final String INPUT_CONTEXT_OID = "contextOid";
  private static final String INPUT_COURSE_VIEW = "section";
  private static final String INPUT_GRADE_SCALE = "gradeScaleOid";
  private static final String INPUT_GRADE_TERM_OID = "gradeTermOid";
  private static final String INPUT_HIDDEN_ASSIGNMENTS = "hiddenAssignments";
  private static final String INPUT_QUERY_BY = "queryBy";
  private static final String INPUT_QUERY_STRING = "queryString";
  private static final String INPUT_SCHEDULE_SORT = "scheduleSort";
  private static final String INPUT_STUDENT_SORT = "studentSort";
  private static final String INPUT_TEACHER_NAME = "teacher";
  private static final String INPUT_THRESHOLD = "threshold";

  // Output parameters
  private static final String GRADE_TERM = "gradeTerm";

  /**
   * Member variables
   */
  private Map<String, String> m_sectionOidUserOidMap;
  private Map<String, AverageCalculatorFactory> m_calculatorFactories;
  private SisDistrictSchoolYearContext m_context;
  private SisStudent m_currentStudent;
  private GradeTerm m_gradeTerm;
  private GradeTermDate m_gradeTermDate;
  private GradesManager m_gradesManager;
  private Double m_threshold;
  private Criteria m_studentCriteria;
  private Collection<SisStudent> m_students;
  private Criteria m_studentScheduleCriteria;
  private Map<String, CategoryAverageCalculator> m_categoryCalculators;
  private Map<String, Map<String, Collection<GradebookColumnType>>> m_categories;

  /**
   * @see com.x2dev.sis.tools.reports.ReportJavaSourceDori#gatherData()
   *
   * @return JRDataSource Data source for iReport
   */
  @Override
  protected JRDataSource gatherData() {

    boolean separate = ((Boolean) getParameter(INPUT_SEPARATE_PAGES)).booleanValue();

    ReportDataGrid grid = new ReportDataGrid(100, 10);

    QueryByCriteria query = new QueryByCriteria(StudentSchedule.class, m_studentScheduleCriteria);
    applySortOrder(query);

    int studentSort = ((Integer) getParameter(INPUT_STUDENT_SORT)).intValue();

    loadStudents();

    QueryIterator studentSchedules = getBroker().getIteratorByQuery(query);
    try {
      while (studentSchedules.hasNext()) {
        StudentSchedule studentSchedule = (StudentSchedule) studentSchedules.next();
        Section section = studentSchedule.getSection();
        SisStudent student = studentSchedule.getStudent();

        if (!StringUtils.isEmpty(section.getDescription()) &&
            !section.getDescription().contains(HOMEROOM_KEY)
            && !section.getDescription().contains(LUNCH_KEY)) {
          Set atRiskCategories = getAtRiskCategories(section, student.getOid());

          // logToolMessage(Level.INFO, "Course: " + section.getCourseView() + "-" +
          // section.getDescription() , false);
          // logToolMessage(Level.INFO, "atRiskCategories: " + atRiskCategories, false);

          if (!atRiskCategories.isEmpty()) {
            int counter = 0;
            for (Object object : atRiskCategories) {
              KeyValuePair kvPair = (KeyValuePair) object;

              grid.append();
              grid.set(GRID_STUDENT, student);
              grid.set(GRID_SECTION, section);
              grid.set(GRID_CATEGORY, kvPair.getKey());
              grid.set(GRID_TEACHER, section.getStaffView());

              String raw = (String) kvPair.getValue();
              Double numberGrade = Double.valueOf(raw.substring(0, raw.indexOf('_')));
              String letterGrade = raw.substring(raw.indexOf('_') + 1);

              grid.set(String.valueOf(m_gradeTerm.getGradeTermNum()), numberGrade);
              grid.set(GRID_LETTER_GRADE, letterGrade);

              /*
               * The counter is placed on the grid as a flag for iReport.
               */
              grid.set(GRID_ITEM_COUNT, Integer.valueOf(counter));

              /*
               * Size is being used as a spacer in iReport
               */
              grid.set(GRID_SIZE, Integer.valueOf(atRiskCategories.size()));

              // logToolMessage(Level.INFO, "Grid : " + grid, false);

              /*
               * Set break in iReport based on user-selected sort order
               */
              switch (studentSort) {
                case 1: // yog
                  String yog = String.valueOf(student.getYog());
                  grid.set(GRID_BREAK, ((separate) ? yog : NO_BREAK));
                  grid.set(GRID_SEPARATE, ((separate) ? yog : NO_BREAK));
                  break;

                case 2: // Home room
                  String homeroom = student.getHomeroom(getCurrentContext().getOid(), getBroker());
                  grid.set(GRID_BREAK, ((separate) ? homeroom : NO_BREAK));
                  grid.set(GRID_SEPARATE, ((separate) ? homeroom : NO_BREAK));
                  break;

                default: // Name
                  grid.set(GRID_BREAK, NO_BREAK);
                  grid.set(GRID_SEPARATE, NO_BREAK);
              }
              counter++;
            }
          }
        }
      }
    } finally {
      studentSchedules.close();
    }

    addParameter(GRADE_TERM, m_gradeTerm);

    grid.beforeTop();

    return grid;
  }

  /**
   * @see com.x2dev.sis.tools.ToolJavaSource#initialize()
   */
  @Override
  protected void initialize() {
    new HashMap<String, Map<String, List<SystemPreference>>>();
    m_sectionOidUserOidMap = new HashMap<String, String>();
    String contextOid = (String) getParameter(INPUT_CONTEXT_OID);
    m_context = (SisDistrictSchoolYearContext) getBroker().getBeanByOid(SisDistrictSchoolYearContext.class, contextOid);

    String gradeTermOid = (String) getParameter(INPUT_GRADE_TERM_OID);

    m_gradesManager = new GradesManager(getBroker());
    m_gradeTermDate = GradesManager.getGradeTermDate(gradeTermOid,
        ((SisSchool) getSchool()).getOid(), contextOid, getBroker());
    m_gradeTerm = ((GradeTerm) getBroker().getBeanByOid(GradeTerm.class, gradeTermOid));

    new HashMap<String, TermAverageCalculator>();
    m_calculatorFactories = new HashMap<String, AverageCalculatorFactory>();
    m_categoryCalculators = new HashMap<String, CategoryAverageCalculator>();

    buildCriteria();
    loadGradebookCategories();

    try {
      m_threshold = Double.valueOf(Double.parseDouble((String) getParameter(INPUT_THRESHOLD)));
    } catch (NumberFormatException nfe) {
      m_threshold = Double.valueOf(0.0);
      logToolMessage(Level.SEVERE, FORMAT_ERROR
          + nfe.getMessage(), false);
    }
  }

  /**
   * @see com.x2dev.sis.tools.ToolJavaSource#saveState(com.x2dev.sis.web.UserDataContainer)
   *
   * @param userData UserDataContainer
   */
  @Override
  protected void saveState(UserDataContainer userData) {
    /*
     * If we're in the context of a single student, run for just that student.
     */
    m_currentStudent = (SisStudent) userData.getCurrentRecord(SisStudent.class);
  }

  /**
   * Applies the student and schedule sorts to the passed query.
   *
   * @param query
   */
  private void applySortOrder(QueryByCriteria query) {
    int studentSort = ((Integer) getParameter(INPUT_STUDENT_SORT)).intValue();
    /* who cares about sorting - plus this broke stuff */
    /*
     * switch (studentSort) {
     * case 1: // YOG
     * query.addOrderByAscending(StudentSchedule.REL_STUDENT + PATH_DELIMITER +
     * SisStudent.COL_YOG);
     * break;
     * 
     * case 2: // Homeroom
     * query.addOrderByAscending(StudentSchedule.REL_STUDENT + PATH_DELIMITER +
     * SisStudent.COL_HOMEROOM);
     * break;
     * 
     * default: // Name
     * break;
     * }
     * 
     * query.addOrderByAscending(StudentSchedule.REL_STUDENT + PATH_DELIMITER +
     * SisStudent.COL_NAME_VIEW);
     */
    /*
     * int scheduleSort = ((Integer) getParameter(INPUT_SCHEDULE_SORT)).intValue();
     * switch (scheduleSort) {
     * case 0: // Number
     * query.addOrderByAscending(StudentSchedule.REL_SECTION + PATH_DELIMITER +
     * Section.COL_COURSE_VIEW);
     * break;
     * 
     * case 1: // Description
     * query.addOrderByAscending(StudentSchedule.REL_SECTION + PATH_DELIMITER
     * + Section.REL_SCHOOL_COURSE + PATH_DELIMITER + SchoolCourse.COL_DESCRIPTION);
     * break;
     * 
     * default:
     * break;
     * }
     */
  }

  /**
   * Builds <code>attendanceCriteria</code>, <code>studentCriteria</code>, and
   * <code>studentScheduleCriteria</code>.
   */
  private void buildCriteria() {
    /*
     * Initialize the criteria objects
     */
    m_studentCriteria = new Criteria();
    m_studentScheduleCriteria = new Criteria();

    /*
     * Add schedule based criteria
     */
    m_studentScheduleCriteria.addEqualTo(
        StudentSchedule.REL_SCHEDULE + PATH_DELIMITER + Schedule.COL_DISTRICT_CONTEXT_OID,
        m_context.getOid());
    m_studentScheduleCriteria.addNotEqualTo(StudentSchedule.REL_SCHEDULE + PATH_DELIMITER +
        Schedule.COL_BUILD_SCENARIO_INDICATOR, Boolean.TRUE);

    /*
     * If we're in the context of a single student, only return the results for that
     * student
     */
    if (m_currentStudent != null) {
      m_studentCriteria.addEqualTo(X2BaseBean.COL_OID, m_currentStudent.getOid());
      m_studentScheduleCriteria.addEqualTo(StudentSchedule.COL_STUDENT_OID, m_currentStudent.getOid());
    } else {
      if (isSchoolContext()) {
        m_studentCriteria.addEqualTo(SisStudent.COL_SCHOOL_OID, ((SisSchool) getSchool()).getOid());
      }

      int queryBy = ((Integer) getParameter(INPUT_QUERY_BY)).intValue();

      switch (queryBy) {
        case 0: // Current selection
          SubQuery studentSubQuery = new SubQuery(SisStudent.class, X2BaseBean.COL_OID, getCurrentCriteria());
          m_studentCriteria.addIn(X2BaseBean.COL_OID, studentSubQuery);
          m_studentScheduleCriteria.addIn(StudentSchedule.COL_STUDENT_OID, studentSubQuery);
          break;

        case 2: // YOG
          m_studentCriteria.addEqualTo(SisStudent.COL_YOG, getParameter(INPUT_QUERY_STRING));
          m_studentScheduleCriteria.addEqualTo(StudentSchedule.REL_STUDENT
              + PATH_DELIMITER + SisStudent.COL_YOG, getParameter(INPUT_QUERY_STRING));
          break;

        case 3: // Homeroom
          m_studentCriteria.addEqualTo(SisStudent.COL_HOMEROOM, getParameter(INPUT_QUERY_STRING));
          m_studentScheduleCriteria.addEqualTo(StudentSchedule.REL_STUDENT
              + PATH_DELIMITER + SisStudent.COL_HOMEROOM, getParameter(INPUT_QUERY_STRING));
          break;

        case 4: // Snapshot
          SubQuery recordSetSubQuery = ReportUtils.getRecordSetSubQuery(
              (String) getParameter(INPUT_QUERY_STRING), (SisUser) getUser(), (SisSchool) getSchool());
          m_studentCriteria.addIn(X2BaseBean.COL_OID, recordSetSubQuery);
          m_studentScheduleCriteria.addIn(StudentSchedule.COL_STUDENT_OID, recordSetSubQuery);
          break;

        default: // All
          break;
      }

      String teacher = (String) getParameter(INPUT_TEACHER_NAME);
      if (!StringUtils.isEmpty(teacher)) {
        Criteria teacherCriteria = new Criteria();
        teacherCriteria.addEqualTo(StudentSchedule.REL_SECTION + PATH_DELIMITER + Section.REL_PRIMARY_STAFF
            + PATH_DELIMITER + SisStaff.COL_NAME_VIEW, teacher);

        SubQuery subQuery = new SubQuery(StudentSchedule.class, StudentSchedule.COL_STUDENT_OID, teacherCriteria);
        m_studentCriteria.addIn(X2BaseBean.COL_OID, subQuery);
        m_studentScheduleCriteria.addEqualTo(StudentSchedule.REL_SECTION + PATH_DELIMITER +
            Section.REL_PRIMARY_STAFF + PATH_DELIMITER + SisStaff.COL_NAME_VIEW, teacher);
      }

      String course = (String) getParameter(INPUT_COURSE_VIEW);
      if (!StringUtils.isEmpty(course)) {
        Criteria sectionCriteria = new Criteria();
        sectionCriteria.addEqualTo(StudentSchedule.REL_SECTION + PATH_DELIMITER +
            Section.COL_COURSE_VIEW, course);

        SubQuery courseSubQuery = new SubQuery(StudentSchedule.class, StudentSchedule.COL_STUDENT_OID, sectionCriteria);
        m_studentCriteria.addIn(X2BaseBean.COL_OID, courseSubQuery);
        m_studentScheduleCriteria.addEqualTo(StudentSchedule.REL_SECTION + PATH_DELIMITER +
            Section.COL_COURSE_VIEW, course);
      }

      boolean activeOnly = ((Boolean) getParameter(INPUT_ACTIVE_ONLY)).booleanValue();
      if (activeOnly) {
        String activeCode = PreferenceManager.getPreferenceValue(getOrganization(), STUDENT_ACTIVE_CODE);

        m_studentCriteria.addEqualTo(SisStudent.COL_ENROLLMENT_STATUS, activeCode);
        m_studentScheduleCriteria.addEqualTo(StudentSchedule.REL_STUDENT
            + PATH_DELIMITER + SisStudent.COL_ENROLLMENT_STATUS, activeCode);
      }
    }

    Collection<String> scheduleTermOids = getScheduleTermOids();
    if (!CollectionUtils.isEmpty(scheduleTermOids)) {
      m_studentScheduleCriteria.addIn(StudentSchedule.REL_SECTION + PATH_DELIMITER
          + Section.COL_SCHEDULE_TERM_OID, scheduleTermOids);
    }
  }

  /**
   * Returns the calculator factory for the passed section.
   *
   * @param section
   *
   * @return AverageCalculatorFactory
   */
  private AverageCalculatorFactory getCalculatorFactory(Section section) {
    GradeScale gradeScale = null;
    int decimals = 2;
    int calculationMode = 0;
    String strCalculationMode = "0";

    SisUser staffUser = null;
    SisStaff staff = (section != null) ? section.getPrimaryStaff() : null;

    if (staff != null && staff.getPerson() != null) {
      staffUser = section.getPrimaryStaff().getPerson().getUser(getBroker());
    }

    if (staffUser != null) {
      /*
       * Load the teacher's grading preferences and get an AverageCalculatorFactory
       * for
       * this student
       */
      m_sectionOidUserOidMap.put(section.getOid(), staffUser.getOid());
      getUserPreferenceValues(m_sectionOidUserOidMap.values());

      SisSchool skl = section.getSchedule().getSchool();

      PreferenceSet parentPreferenceSet = PreferenceManager.getPreferenceSet(skl);

      try {
        decimals = Integer.parseInt(PreferenceManager.getPreferenceValue(staffUser,
            parentPreferenceSet, SisPreferenceConstants.GRADES_AVERAGE_DECIMALS));
      } catch (NumberFormatException nfe) {
        logToolMessage(Level.SEVERE, "GetCalculatorFactory error: " + nfe.getMessage(), false);
      }

      calculationMode = GradebookManager.getEffectiveAverageMode(section.getOid(), staffUser,
          parentPreferenceSet, getBroker());

      strCalculationMode = PreferenceManager.getPreferenceValue(staffUser, parentPreferenceSet, GRADEBOOK_AVERAGE_MODE);

      if (strCalculationMode == null || !StringUtils.isNumeric(strCalculationMode)) {
        strCalculationMode = "0";
        calculationMode = Integer.valueOf(strCalculationMode).intValue();
      } else {
        calculationMode = Integer.valueOf(strCalculationMode).intValue();
      }

      String gradeScaleOid = null;

      gradeScaleOid = (String) getParameter(INPUT_GRADE_SCALE);

      gradeScale = (GradeScale) getBroker().getBeanByOid(GradeScale.class, gradeScaleOid);
    }

    /*
     * This is set to include hidden assignments.
     */

    boolean includeHiddenAssignments = ((Boolean) getParameter(INPUT_HIDDEN_ASSIGNMENTS)).booleanValue();

    AverageCalculatorFactory calculatorFactory = new AverageCalculatorFactory(
        (MasterSchedule) section, // The section to calculate averages for
        decimals, // number of decimal places to round to
        m_gradesManager, // Grades Manager to use
        gradeScale, // Grade scale to apply to calculated averages. Can be null in
                    // which case no letter grade and numeric grades will calc to
                    // 0-100 point range
        calculationMode, // Average calculation mode
        false, // If True, this will calculate from a list of standards provided by
               // the next parameter
        new ArrayList(), // Used by preceding parameter. It is a list of standards
                         // to use.
        getBroker(), // X2Broker
        includeHiddenAssignments, // Include hidden assignments
        null, // Data helper/cache for gathering/sharing term average data. Can be
              // null to optimize for one section/all students
        null // StandardsCalcHelper similar to above, but buffers different
             // information needed only by standards bases calculators
    );

    return calculatorFactory;
  }

  /**
   * Returns a set of KeyVauePair objects where the categories have averages
   * falling below
   * user-determined threshold.
   *
   * @param section    Section
   * @param studentOid String
   *
   * @return Set<KeyValuePair<String,String>> atRiskList Set
   */
  private Set<KeyValuePair<String, String>> getAtRiskCategories(Section section, String studentOid) {
    String stfOid = section.getPrimaryStaffOid();
    Map<String, Collection<GradebookColumnType>> staffCategories = m_categories.get(stfOid);

    // logToolMessage(Level.INFO, "atRisk Section : " + section.getCourseView() +
    // "-" + section.getDescription() +
    // "/" + section.getPrimaryStaffOid() + " : " + staffCategories, false);

    Set<KeyValuePair<String, String>> atRiskSet = new HashSet<KeyValuePair<String, String>>();

    if (staffCategories != null) {
      Collection<GradebookColumnType> categories = staffCategories.get(section.getOid());

      AverageCalculatorFactory calculatorFactory = m_calculatorFactories.get(section.getOid());

      if (calculatorFactory == null) {
        calculatorFactory = getCalculatorFactory(section);
        m_calculatorFactories.put(section.getOid(), calculatorFactory);
      }

      // logToolMessage(Level.INFO, section.getOid() + " | " + categories + " | " +
      // m_gradeTerm, false);

      if (categories != null && m_gradeTerm != null) {
        Iterator categoryIterator = categories.iterator();
        while (categoryIterator.hasNext()) {
          GradebookColumnType category = (GradebookColumnType) categoryIterator.next();
          String catType = category.getColumnType();

          CategoryAverageCalculator categoryCalculator = null;

          String categoryKey = getCalculatorKey(section, category.getOid());
          categoryCalculator = m_categoryCalculators.get(categoryKey);

          if (categoryCalculator == null) {
            categoryCalculator = (CategoryAverageCalculator) calculatorFactory
                .getCategoryAverageCalculator(category, m_gradeTerm, m_students);

            m_categoryCalculators.put(categoryKey, categoryCalculator);
          }

          Double avgNumeric = categoryCalculator.getAverageNumeric(studentOid);

          String letterGrade = categoryCalculator.getAverageLetter(studentOid);
          String numberAndLetter = avgNumeric + "_" + letterGrade;

          // logToolMessage(Level.INFO, section.getOid() + " | " + category + " | " +
          // studentOid + letterGrade + "/" + avgNumeric, false);
          /*
           * If a category has a null average, set the return value to 100 to
           * keep it from being considered.
           */
          int returnValue = (avgNumeric != null) ? Double.compare(avgNumeric.doubleValue(),
              m_threshold.doubleValue()) : 100;

          if (avgNumeric != null && returnValue <= 0) {
            KeyValuePair<String, String> kvPair = new KeyValuePair(catType, numberAndLetter);
            atRiskSet.add(kvPair);
          }
        }
      }
    }

    // logToolMessage(Level.INFO, "atRiskSet : " + atRiskSet, false);

    return atRiskSet;
  }

  /**
   * Returns the calculator key for the passed section and secondary key.
   *
   * @param section
   * @param secondaryKey this value could be a category OID or a term OID
   *
   * @return String
   */
  private String getCalculatorKey(Section section, String secondaryKey) {
    return section.getOid() + secondaryKey;
  }

  /**
   * Returns a collection of schedule term OIDs for the grade term of the selected
   * transcript
   * column.
   *
   * @return Collection<String>
   */
  private Collection<String> getScheduleTermOids() {
    Collection<String> scheduleTermOids = null;

    if (m_gradeTermDate != null) {
      Criteria scheduleTermCriteria = new Criteria();
      scheduleTermCriteria.addLessOrEqualThan(ScheduleTermDate.COL_START_DATE, m_gradeTermDate.getEndDate());
      scheduleTermCriteria.addGreaterOrEqualThan(ScheduleTermDate.COL_END_DATE, m_gradeTermDate.getStartDate());
      scheduleTermCriteria.addEqualTo(ScheduleTermDate.REL_SCHEDULE_TERM
          + PATH_DELIMITER + ScheduleTerm.COL_SCHEDULE_OID, ((SisSchool) getSchool()).getActiveScheduleOid());

      SubQuery scheduleTermSubQuery = new SubQuery(ScheduleTermDate.class, ScheduleTermDate.COL_SCHEDULE_TERM_OID,
          scheduleTermCriteria);
      scheduleTermOids = getBroker().getSubQueryCollectionByQuery(scheduleTermSubQuery);
    }

    return scheduleTermOids;
  }

  /**
   * Loads the user's preferences for the keys in the PREFERENCE_KEYS array into a
   * map of maps
   *
   * @param users The users
   *
   * @return Map<String, Map<String,String>> The user preferences maps
   */
  private Map<String, Map<String, List<SystemPreference>>> getUserPreferenceValues(Collection<String> users) {
    return PreferenceManager.getUserPreferenceValuesMap(getBroker(), users, Arrays.asList(PREFERENCE_KEYS));
  }

  /**
   * Loads a map of staff OIDs keyed to maps of master schedule OIDs keyed to
   * collections of
   * grade book columns types.
   */
  private void loadGradebookCategories() {
    SubQuery scheduleSubQuery = new SubQuery(StudentSchedule.class,
        StudentSchedule.COL_SECTION_OID, m_studentScheduleCriteria);

    Criteria criteria = new Criteria();
    criteria.addIn(GradebookColumnType.COL_MASTER_SCHEDULE_OID, scheduleSubQuery);

    QueryByCriteria query = new QueryByCriteria(GradebookColumnType.class, criteria);
    query.addOrderByDescending(GradebookColumnType.COL_COLUMN_TYPE_WEIGHT);
    query.addOrderByAscending(GradebookColumnType.COL_COLUMN_TYPE);

    String[] columns = {
        GradebookColumnType.COL_STAFF_OID, GradebookColumnType.COL_MASTER_SCHEDULE_OID
    };

    m_categories = getBroker().getGroupedCollectionByQuery(query, columns, new int[] {
        128, 16
    });
    // logToolMessage(Level.INFO, "m_Categories : " + m_categories, false);
  }

  /**
   * Loads a collection of all the students.
   */
  private void loadStudents() {
    QueryByCriteria query = new QueryByCriteria(SisStudent.class, m_studentCriteria);
    m_students = getBroker().getCollectionByQuery(query);
  }

}
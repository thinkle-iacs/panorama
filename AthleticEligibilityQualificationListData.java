package com.x2dev.reports.ma.innovation;

/*
 * ====================================================================
 *
 * X2 Development Corporation
 *
 * Copyright (c) 2002-2003 X2 Development Corporation.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, is not permitted without express written agreement
 * from X2 Development Corporation.
 *
 * ====================================================================
 */
import static com.follett.fsc.core.k12.business.ModelProperty.PATH_DELIMITER;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
//import java.util.List;
import java.util.Map;
import com.follett.fsc.core.k12.web.UserDataContainer;

import net.sf.jasperreports.engine.JRDataSource;

import org.apache.ojb.broker.query.QueryByCriteria;

import com.follett.fsc.core.framework.persistence.X2Criteria;
import com.follett.fsc.core.k12.beans.DistrictSchoolYearContext;
import com.follett.fsc.core.k12.beans.SystemPreferenceDefinition;
import com.follett.fsc.core.k12.beans.X2BaseBean;
import com.follett.fsc.core.k12.business.PreferenceManager;
import com.follett.fsc.core.k12.business.PreferenceSet;
import com.follett.fsc.core.k12.tools.reports.ReportDataGrid;
import com.follett.fsc.core.k12.tools.reports.ReportJavaSourceNet;
import com.follett.fsc.core.k12.web.AppGlobals;
import com.x2dev.sis.model.beans.GradeScale;
import com.x2dev.sis.model.beans.GradeTerm;
import com.x2dev.sis.model.beans.MasterSchedule;
import com.x2dev.sis.model.beans.Schedule;
import com.x2dev.sis.model.beans.SchoolScheduleContext;
import com.x2dev.sis.model.beans.Section;
import com.x2dev.sis.model.beans.SisPreferenceConstants;
import com.x2dev.sis.model.beans.SisStaff;
import com.x2dev.sis.model.beans.SisStudent;
import com.x2dev.sis.model.beans.SisUser;
import com.x2dev.sis.model.beans.StudentSchedule;
import com.x2dev.sis.model.business.GradesManager;
import com.x2dev.sis.model.business.gradebook.AverageCalculator;
import com.x2dev.sis.model.business.gradebook.AverageCalculatorFactory;
import com.x2dev.sis.model.business.gradebook.GradebookManager;
import com.x2dev.utils.StringUtils;
import com.x2dev.utils.X2BaseException;

/**
 * Data source for the "Qualification List" report. This report lists students
 * qualifying for all
 * lists in a user specified category.
 * This procedure supports creation of a record set per qualifiaction list
 * within the given
 * category.
 * 
 * @author X2 Development Corporation
 */
public class AthleticEligibilityQualificationListData extends ReportJavaSourceNet {
  // @Override
  // protected void saveState(UserDataContainer userData) throws X2BaseException
  // {
  // runOnApplicationServer();
  // }

  private static final long serialVersionUID = 1L;

  /*
   * Name for the "context OID" input parameter. The value is a String.
   */
  private static final String CONTEXT_OID_PARAM = "contextOid";

  /*
   * Name for the "query by" input parameter. The value is a String.
   */
  private static final String QUERY_BY_PARAM = "queryBy";

  /*
   * Name for the "query string" input parameter. The value is a String.
   */
  private static final String QUERY_STRING_PARAM = "queryString";

  /*
   * Other Parameters
   */
  private static final String CONTEXT_PARAM = "context";
  // private static final String SORT_PARAM = "studentSort";
  private static final String STUDENT_SORT_PARAM = "studentSort";
  private static final String TERM_PARAM = "gradeTermOid";
  private static final String THRESHOLD_PARAM = "threshold";

  // Grid fields
  private static final String COL_ADVISORY = "advisory";
  private static final String COL_COURSE_VIEW = "courseView";
  private static final String COL_GRADE_AVERAGE = "average";
  private static final String COL_QUALIFICATION_LIST = "list";
  private static final String COL_SECTION = "section";
  private static final String COL_STUDENT = "student";
  private static final String COL_STUDENT_NAMEVIEW = "nameView";
  private static final String COL_YOG = "yog";

  // Member variables
  private GradesManager m_gradesManager;
  private Map<String, AverageCalculatorFactory> m_calculatorFactories;
  private Map<String, AverageCalculator> m_calculatorMap = null;
  private DistrictSchoolYearContext m_context;
  private Map<String, GradeScale> m_gradeScales;
  private GradeTerm m_gradeTerm;
  // private List<String> m_ineligibleStudents;
  private Collection<SisStudent> m_students;
  private Map<String, Collection<StudentSchedule>> m_studentScheduleMap = null;
  private int m_studentSort;
  private Double m_threshold;

  /**
   * @see com.follett.fsc.core.k12.tools.reports.ReportJavaSourceDori#gatherData()
   */
  @Override
  protected JRDataSource gatherData() {
    ReportDataGrid grid = new ReportDataGrid(1000, 5);

    String sectionOid = null;
    MasterSchedule section = null;
    GradeScale gradeScale = null;
    AverageCalculator calculator = null;

    for (SisStudent currentStudent : m_students) {
      if (getStudentSchedules(currentStudent) != null) {
        for (StudentSchedule schedule : getStudentSchedules(currentStudent)) {
          if (!schedule.getSectionOid().equals(sectionOid)) {
            section = schedule.getSection();
            gradeScale = getGradeScale(section);

            calculator = getCalculator(section, gradeScale);
            sectionOid = schedule.getSectionOid();
          }

          Double value = calculator.getAverageNumeric(currentStudent.getOid());

          if (value != null && value.doubleValue() < m_threshold.doubleValue()) {
            DecimalFormat decimalFormat = new DecimalFormat("###.#");
            grid.append();
            grid.set(COL_STUDENT, currentStudent);
            grid.set(COL_QUALIFICATION_LIST, "Ineligible Students");
            grid.set(COL_SECTION, section);
            grid.set(COL_GRADE_AVERAGE, Double.valueOf(decimalFormat.format(value)));
            grid.set(COL_STUDENT_NAMEVIEW, currentStudent.getNameView());
            grid.set(COL_YOG, Integer.valueOf(currentStudent.getYog()));
            grid.set(COL_COURSE_VIEW, section.getDescription());
            grid.set(COL_ADVISORY, currentStudent.getFieldB043());

            // AppGlobals.getLog().severe("GRID: " + grid);
          }
        }
      }
    }

    addParameter(CONTEXT_PARAM, m_context);

    grid.beforeTop();
    if (m_studentSort == 0) {
      grid.sort(Arrays.asList(COL_STUDENT_NAMEVIEW, COL_COURSE_VIEW), false);
    } else if (m_studentSort == 1) {
      grid.sort(Arrays.asList(COL_YOG, COL_STUDENT_NAMEVIEW, COL_COURSE_VIEW), false);
    } else if (m_studentSort == 2) {
      grid.sort(Arrays.asList(COL_ADVISORY, COL_STUDENT_NAMEVIEW, COL_COURSE_VIEW), false);
    }

    return grid;
  }

  @Override
  protected void initialize() throws X2BaseException {
    super.initialize();

    m_calculatorFactories = new HashMap<String, AverageCalculatorFactory>();
    m_calculatorMap = new HashMap<String, AverageCalculator>();
    m_gradeScales = new HashMap<String, GradeScale>();
    m_gradesManager = new GradesManager(getBroker());
    // m_ineligibleStudents = new ArrayList<String>();
    m_students = new ArrayList<SisStudent>();
    m_studentScheduleMap = new HashMap<String, Collection<StudentSchedule>>();
    m_studentSort = ((Integer) getParameter(STUDENT_SORT_PARAM)).intValue();

    String threshold = (String) getParameter(THRESHOLD_PARAM);
    m_threshold = Double.valueOf(threshold);

    String contextOid = (String) getParameter(CONTEXT_OID_PARAM);
    m_context = (DistrictSchoolYearContext) getBroker().getBeanByOid(DistrictSchoolYearContext.class, contextOid);

    String termOid = (String) getParameter(TERM_PARAM);
    if (!StringUtils.isEmpty(termOid)) {
      m_gradeTerm = (GradeTerm) getBroker().getBeanByOid(GradeTerm.class, termOid);
      addParameter("gradeTermCode", m_gradeTerm.getGradeTermId());
    }

    loadStudents();
  }

  /**
   * Returns the average calculator for the passed section.
   * 
   * @param section
   * @return AverageCalculator
   */
  private AverageCalculator getAverageCalculator(AverageCalculatorFactory calculatorFactory) {
    AverageCalculator averageCalculator = null;

    if (m_gradeTerm != null) {
      averageCalculator = calculatorFactory.getTermAverageCalculator(m_gradeTerm, m_students);
    } else {
      averageCalculator = calculatorFactory.getOverallAverageCalculator(m_students);
    }

    return averageCalculator;
  }

  /**
   * Creates a map of AverageCalculators keyed to section OID's.
   * 
   * @param section
   * @param gradeScale
   * @return
   */
  private AverageCalculator getCalculator(MasterSchedule section, GradeScale gradeScale) {
    AverageCalculatorFactory calculatorFactory = null;
    AverageCalculator calculator = null;

    calculator = m_calculatorMap.get(section.getOid());
    if (calculator == null) {
      calculatorFactory = getCalculatorFactory(section, gradeScale);
      calculator = getAverageCalculator(calculatorFactory);

      m_calculatorMap.put(section.getOid(), calculator);
    }

    return calculator;
  }

  /**
   * Returns the calculator factory for the passed section.
   * 
   * @param section
   * @param gradeScale
   * 
   * @return AverageCalculatorFactory
   */
  private AverageCalculatorFactory getCalculatorFactory(Section section, GradeScale gradeScale) {
    AverageCalculatorFactory calculatorFactory = m_calculatorFactories.get(section.getOid());

    if (calculatorFactory == null && section.getPrimaryStaff() != null) {
      int decimals = 2;
      int averageMode = 1;

      SisUser staffUser = (section.getPrimaryStaff().getPerson().getUser(getBroker()));
      if (staffUser != null) {
        /*
         * Load the teacher's grading preferences and get an AverageCalculatorFactory
         * for
         * this student
         */
        PreferenceSet parentPreferenceSet = PreferenceManager.getPreferenceSet(section.getSchedule().getSchool());
        averageMode = GradebookManager.getEffectiveAverageMode(section.getOid(), staffUser, parentPreferenceSet,
            getBroker());

        String decimalsAsString = PreferenceManager.getPreferenceValue(staffUser, parentPreferenceSet,
            SisPreferenceConstants.GRADES_AVERAGE_DECIMALS);
        if (StringUtils.isNumeric(decimalsAsString)) {
          decimals = Integer.parseInt(decimalsAsString);
        }
      }

      calculatorFactory = new AverageCalculatorFactory((MasterSchedule) section, decimals,
          m_gradesManager, gradeScale, averageMode, false, new ArrayList(), getBroker(), true, null);

      m_calculatorFactories.put(section.getOid(), calculatorFactory);
    }

    return calculatorFactory;
  }

  /**
   * Returns the grade scale for the passed section.
   * 
   * @param section
   * 
   * @return GradeScale
   */
  private GradeScale getGradeScale(Section section) {
    GradeScale gradeScale = m_gradeScales.get(section.getOid() + section.getPrimaryStaffOid());

    if (gradeScale == null) {
      SisUser staffUser = (section.getPrimaryStaff().getPerson().getUser(getBroker()));
      if (staffUser != null) {
        PreferenceSet parentPreferenceSet = PreferenceManager.getPreferenceSet(section.getSchedule().getSchool());
        String gradeScaleOid = PreferenceManager.getPreferenceValue(staffUser, parentPreferenceSet,
            SisPreferenceConstants.GRADEBOOK_AVERAGES_GRADESCALE);

        gradeScale = (GradeScale) getBroker().getBeanByOid(GradeScale.class, gradeScaleOid);
        m_gradeScales.put(section.getOid() + section.getPrimaryStaffOid(), gradeScale);
      }
    }

    return gradeScale;
  }

  /**
   * Returns the collection of student schedules. If the collection is empty, the
   * collection is then created.
   */
  private Collection<StudentSchedule> getStudentSchedules(SisStudent student) {
    if (m_studentScheduleMap.isEmpty()) {
      loadStudentSchedulesMap();
    }

    return m_studentScheduleMap.get(student.getOid());
  }

  /**
   * Load students to calculate average values for.
   */
  private void loadStudents() {
    String activeCode = PreferenceManager.getPreferenceValue(getOrganization(),
        SystemPreferenceDefinition.STUDENT_ACTIVE_CODE);

    X2Criteria criteria = new X2Criteria();

    if (isSchoolContext()) {
      criteria.addEqualTo(SisStudent.COL_SCHOOL_OID, getSchool().getOid());
    }

    criteria.addEqualTo(SisStudent.COL_ENROLLMENT_STATUS, activeCode);
    addUserCriteria(criteria,
        ((String) getParameter(QUERY_BY_PARAM)).replace(StudentSchedule.REL_STUDENT + PATH_DELIMITER, ""),
        (String) getParameter(QUERY_STRING_PARAM),
        SisStudent.class,
        X2BaseBean.COL_OID);

    QueryByCriteria query = new QueryByCriteria(SisStudent.class, criteria);
    m_students = getBroker().getCollectionByQuery(query);
  }

  /**
   * Creates a map of collections of student schedules keyed to the student OID's
   */
  private void loadStudentSchedulesMap() {
    X2Criteria criteria = new X2Criteria();

    addUserCriteria(criteria,
        (String) getParameter(QUERY_BY_PARAM),
        (String) getParameter(QUERY_STRING_PARAM),
        SisStudent.class,
        StudentSchedule.REL_STUDENT + PATH_DELIMITER + X2BaseBean.COL_OID);

    criteria.addEqualTo(StudentSchedule.REL_SCHEDULE + PATH_DELIMITER +
        Schedule.REL_ACTIVE_SCHOOL_SCHEDULE_CONTEXTS + PATH_DELIMITER +
        SchoolScheduleContext.COL_DISTRICT_CONTEXT_OID, getOrganization().getRootOrganization().getCurrentContextOid());

    criteria.addNotNull(StudentSchedule.REL_SECTION + PATH_DELIMITER +
        Schedule.REL_MASTER_SCHEDULES + PATH_DELIMITER +
        MasterSchedule.REL_PRIMARY_STAFF + PATH_DELIMITER + X2BaseBean.COL_OID);

    QueryByCriteria query = new QueryByCriteria(StudentSchedule.class, criteria);
    query.addOrderByAscending(StudentSchedule.COL_STUDENT_OID);
    query.addOrderByAscending(StudentSchedule.COL_SECTION_OID);

    m_studentScheduleMap = getBroker().getGroupedCollectionByQuery(query, StudentSchedule.COL_STUDENT_OID, 2000);
  }
}
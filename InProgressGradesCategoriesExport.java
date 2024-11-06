package aspen.exports.panorama;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.text.DecimalFormat;
import java.util.logging.Level;

import org.apache.ojb.broker.query.Criteria;
import org.apache.ojb.broker.query.QueryByCriteria;

import com.follett.fsc.core.k12.beans.QueryIterator;
import com.follett.fsc.core.k12.beans.Student;
import com.follett.fsc.core.k12.beans.SystemPreferenceDefinition;
import com.follett.fsc.core.k12.business.PreferenceManager;
import com.follett.fsc.core.k12.business.PreferenceSet;
import com.follett.fsc.core.k12.tools.reports.ReportDataGrid;
import com.x2dev.sis.model.beans.GradeScale;
import com.x2dev.sis.model.beans.GradeTerm;
import com.x2dev.sis.model.beans.GradebookColumnType;
import com.x2dev.sis.model.beans.MasterSchedule;
import com.x2dev.sis.model.beans.Schedule;
import com.x2dev.sis.model.beans.SchoolScheduleContext;
import com.x2dev.sis.model.beans.Section;
import com.x2dev.sis.model.beans.SectionReportingStandard;
import com.x2dev.sis.model.beans.SisPreferenceConstants;
import com.x2dev.sis.model.beans.SisSchool;
import com.x2dev.sis.model.beans.SisStaff;
import com.x2dev.sis.model.beans.SisStudent;
import com.x2dev.sis.model.beans.SisUser;
import com.x2dev.sis.model.beans.StudentSchedule;
import com.x2dev.sis.model.business.GradesManager;
import com.x2dev.sis.model.business.gradebook.AverageCalculatorFactory;
import com.x2dev.sis.model.business.gradebook.CategoryAverageCalculator;
import com.x2dev.sis.model.business.gradebook.AverageCalculator;
import com.x2dev.sis.model.business.gradebook.GradebookManager;
import com.x2dev.sis.model.business.gradebook.TermAverageCalculator;
import com.x2dev.utils.StringUtils;
import com.x2dev.utils.X2BaseException;

/* Copied from Categories Report */

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

public class InProgressGradesCategoriesExport extends PanoramaExportBase {
    private static final String STUDENT_ID = "student_id";
    private static final String SECTION_ID = "section_id";
    private static final String MARKING_PERIOD_ID = "marking_period_id";
    private static final String COURSE_NAME = "course_name";
    private static final String CATEGORY_NAME = "category_name";
    private static final String GRADE = "grade";
    private static final String IS_COURSE = "is_real_course";
    private static final String GRADE_SLOT = "grade_slot";
    private static final String SCHOOL_ID = "school_id";

    private List<String> m_columns = Arrays.asList(
            STUDENT_ID, SECTION_ID, MARKING_PERIOD_ID,
            COURSE_NAME, CATEGORY_NAME, GRADE, IS_COURSE, GRADE_SLOT, SCHOOL_ID);

    /**
     * Member variables
     */
    private Map<String, AverageCalculatorFactory> m_calculatorFactories;
    private Map<String, GradeTerm> m_currentGradeTerms;
    private Map<String, GradeScale> m_gradeScales;
    private GradesManager m_gradesManager;
    private Collection<SisStudent> m_students;
    private Map<String, TermAverageCalculator> m_termCalculators;
    private Map<String, Collection<GradebookColumnType>> m_sectionCategories;
    private Map<String, CategoryAverageCalculator> m_categoryCalculators;
    private Criteria m_studentCriteria;
    private Criteria m_studentScheduleCriteria;
    private Map<String, Map<String, Collection<GradebookColumnType>>> m_categories;

    @Override
    protected void loadGrid(ReportDataGrid grid) {
        // Define criteria to fetch all GradebookColumnTypes associated with active
        // sections
        Criteria criteria = new Criteria();
        criteria.addEqualTo(
                GradebookColumnType.REL_MASTER_SCHEDULE + "." + MasterSchedule.REL_SCHOOL + "." + SisSchool.COL_OID,
                getSchool().getOid());

        QueryByCriteria query = new QueryByCriteria(GradebookColumnType.class, criteria);
        query.addOrderByDescending(GradebookColumnType.COL_COLUMN_TYPE_WEIGHT);
        query.addOrderByAscending(GradebookColumnType.COL_COLUMN_TYPE);

        // Group by MasterSchedule OID is removed since it's not needed
        String groupingColumn = GradebookColumnType.COL_MASTER_SCHEDULE_OID;
        m_sectionCategories = getBroker().getGroupedCollectionByQuery(query, groupingColumn);

        // Now, fetch all StudentSchedules associated with the school
        Criteria studentScheduleCriteria = new Criteria();
        studentScheduleCriteria.addEqualTo(
                StudentSchedule.REL_SCHEDULE + "." + Schedule.REL_ACTIVE_SCHOOL_SCHEDULE_CONTEXTS + "." +
                        SchoolScheduleContext.COL_DISTRICT_CONTEXT_OID,
                getCurrentContext().getOid());
        applySchoolFilterCriteria(studentScheduleCriteria, StudentSchedule.REL_SCHEDULE + "." + Schedule.REL_SCHOOL);

        QueryByCriteria studentScheduleQuery = new QueryByCriteria(StudentSchedule.class, studentScheduleCriteria);
        studentScheduleQuery.addOrderByAscending(StudentSchedule.REL_STUDENT + "." + Student.COL_LOCAL_ID);
        studentScheduleQuery.addOrderByAscending(StudentSchedule.REL_SECTION + "." + Section.COL_COURSE_VIEW);

        QueryIterator<StudentSchedule> iterator = getBroker().getIteratorByQuery(studentScheduleQuery);
        try {
            while (iterator.hasNext()) {
                StudentSchedule studentSchedule = iterator.next();
                try {
                    appendGradeInformation(grid, studentSchedule);
                } catch (Exception e) {
                    // Log the exception and continue with the next student
                    logToolMessage(Level.SEVERE,
                            "Error processing student OID " + studentSchedule.getStudentOid() + ": " + e.getMessage(),
                            false);
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            // Log any exceptions that occur outside of the per-iteration try-catch
            logToolMessage(Level.SEVERE, "Error in loadGrid method: " + e.getMessage(), false);
            e.printStackTrace();
        } finally {
            iterator.close();
        }
    }

    private void appendGradeInformation(ReportDataGrid grid, StudentSchedule studentSchedule) {
        Section section = studentSchedule.getSection();

        AverageCalculatorFactory calculatorFactory = m_calculatorFactories.get(section.getOid());
        if (calculatorFactory == null) {
            calculatorFactory = getCalculatorFactory(section);
            m_calculatorFactories.put(section.getOid(), calculatorFactory);
        }

        Collection<GradeTerm> terms = m_gradesManager.getCurrentGradeTerms(
                section.getSchedule().getSchool().getOid(), true);
        for (GradeTerm term : terms) {
            if (term != null) {
                Collection<GradebookColumnType> categories = m_sectionCategories.get(section.getOid());
                if (categories != null) {
                    for (GradebookColumnType category : categories) {
                        // Use AverageCalculator instead of CategoryAverageCalculator
                        AverageCalculator categoryCalculator = calculatorFactory.getCategoryAverageCalculator(
                                category, term, m_students);

                        Double avgNumeric = categoryCalculator.getAverageNumeric(studentSchedule.getStudentOid());
                        if (avgNumeric != null) {
                            String grade = formatGrade(avgNumeric);
                            grid.append();
                            setCategoryGradeInformation(grid, studentSchedule, term, category, grade);
                        }
                    }
                }
            }
        }
    }

    /**
     * Formats the numeric grade to two decimal places.
     *
     * @param gradeValue The numeric grade to format.
     * @return A formatted string representing the grade. Returns an empty string if
     *         gradeValue is null.
     */
    private String formatGrade(Double gradeValue) {
        if (gradeValue == null) {
            return "";
        }
        DecimalFormat df = new DecimalFormat("0.00"); // Fixed two decimal places
        return df.format(gradeValue);
    }

    /**
     * Sets the category grade information into the grid.
     *
     * @param grid            The ReportDataGrid instance.
     * @param studentSchedule The StudentSchedule instance.
     * @param term            The GradeTerm instance.
     * @param category        The GradebookColumnType instance.
     * @param gradeValue      The formatted grade value.
     */
    private void setCategoryGradeInformation(ReportDataGrid grid, StudentSchedule studentSchedule, GradeTerm term,
            GradebookColumnType category, String gradeValue) {
        grid.set(STUDENT_ID, studentSchedule.getStudent().getLocalId());
        grid.set(SECTION_ID, studentSchedule.getSectionOid());
        grid.set(COURSE_NAME, studentSchedule.getSection().getDescription());
        grid.set(CATEGORY_NAME, category.getColumnType());
        grid.set(GRADE, gradeValue);
        grid.set(IS_COURSE, Boolean.TRUE.toString());
        grid.set(GRADE_SLOT, term.getGradeTermId());

        SisSchool school = studentSchedule.getSection().getSchedule().getSchool();
        grid.set(MARKING_PERIOD_ID, getMarkingPeriodId(school, term));
        grid.set(SCHOOL_ID, school.getSchoolId());
    }

    /**
     * Returns the calculator factory for the passed section.
     *
     * @param section The section for which to get the calculator factory.
     * @return AverageCalculatorFactory instance.
     */
    private AverageCalculatorFactory getCalculatorFactory(Section section) {
        GradeScale gradeScale = null;
        int decimals = 2; // Fixed decimal places
        int averageMode = 1;

        SisStaff primaryStaff = section.getPrimaryStaff();
        if (primaryStaff != null) {
            SisUser staffUser = primaryStaff.getPerson().getUser(getBroker());

            if (staffUser != null) {
                /*
                 * Load the teacher's grading preferences and get an AverageCalculatorFactory
                 * for this student
                 */
                PreferenceSet parentPreferenceSet = PreferenceManager
                        .getPreferenceSet(section.getSchedule().getSchool());
                averageMode = GradebookManager.getEffectiveAverageMode(section.getOid(), staffUser, parentPreferenceSet,
                        getBroker());

                String gradeScaleKey = getCalculatorKey(section, section.getPrimaryStaffOid());
                gradeScale = m_gradeScales.get(gradeScaleKey);
                if (gradeScale == null) {
                    String gradeScaleOid = PreferenceManager.getPreferenceValue(staffUser, parentPreferenceSet,
                            SisPreferenceConstants.GRADEBOOK_AVERAGES_GRADESCALE);
                    gradeScale = (GradeScale) getBroker().getBeanByOid(GradeScale.class, gradeScaleOid);
                    if (gradeScale != null) {
                        m_gradeScales.put(gradeScaleKey, gradeScale);
                    } else {
                        logToolMessage(Level.WARNING, "GradeScale not found for OID: " + gradeScaleOid, false);
                    }
                }
            }
        }

        AverageCalculatorFactory calculatorFactory = new AverageCalculatorFactory(
                (MasterSchedule) section,
                decimals,
                m_gradesManager,
                gradeScale, // Can be null if not found
                averageMode,
                false,
                new ArrayList<SectionReportingStandard>(),
                getBroker(),
                true, // include grade scale is true
                null,
                null);

        return calculatorFactory;
    }

    /**
     * Returns the calculator key for the passed section and secondary key.
     *
     * @param section      The section.
     * @param secondaryKey This value could be a category OID or a term OID.
     * @return A unique string key.
     */
    private String getCalculatorKey(Section section, String secondaryKey) {
        return section.getOid() + secondaryKey;
    }

    @Override
    protected List<String> getColumns() {
        return m_columns;
    }

    @Override
    protected void initialize() throws X2BaseException {
        m_calculatorFactories = new HashMap<>();
        m_gradeScales = new HashMap<>();
        m_gradesManager = new GradesManager(getBroker());
        m_termCalculators = new HashMap<>();
        m_sectionCategories = new HashMap<>();

        // Load students and section categories
        loadStudents();
        loadGradebookCategories();
    }

    /**
     * Loads a collection of all the students.
     */
    private void loadStudents() {
        Criteria criteria = new Criteria();
        criteria.addEqualTo(SisStudent.COL_ENROLLMENT_STATUS, PreferenceManager.getPreferenceValue(
                getOrganization(), SystemPreferenceDefinition.STUDENT_ACTIVE_CODE));
        applySchoolFilterCriteria(criteria, SisStudent.REL_SCHOOL);

        QueryByCriteria query = new QueryByCriteria(SisStudent.class, criteria);
        m_students = getBroker().getCollectionByQuery(query);
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
        logToolMessage(Level.INFO, "m_Categories : " + m_categories, false);
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
        }

        Collection<String> scheduleTermOids = getScheduleTermOids();
        if (!CollectionUtils.isEmpty(scheduleTermOids)) {
            m_studentScheduleCriteria.addIn(StudentSchedule.REL_SECTION + PATH_DELIMITER
                    + Section.COL_SCHEDULE_TERM_OID, scheduleTermOids);
        }
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

}
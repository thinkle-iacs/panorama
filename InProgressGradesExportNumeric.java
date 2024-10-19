/**
 * This file succesfully outputs grades with a number in our 4 point scale,
 * which is I think what you're looking for -- the grade is in quotation
 * marks in the CSV, but is just a number on our 4 point scale.
 */

package aspen.exports.panorama;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.lang.Double;
import java.text.DecimalFormat;
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
import com.x2dev.sis.model.business.gradebook.GradebookManager;
import com.x2dev.sis.model.business.gradebook.TermAverageCalculator;
import com.x2dev.utils.StringUtils;
import com.x2dev.utils.X2BaseException;

public class InProgressGradesExport extends PanoramaExportBase {
    private static final String STUDENT_ID = "student_id";
    private static final String SECTION_ID = "section_id";
    private static final String MARKING_PERIOD_ID = "marking_period_id";
    private static final String COURSE_NAME = "course_name";
    private static final String GRADE = "grade";
    private static final String IS_COURSE = "is_real_course";
    private static final String GRADE_SLOT = "grade_slot";
    private static final String SCHOOL_ID = "school_id";

    private List<String> m_columns = Arrays.asList(STUDENT_ID, SECTION_ID, MARKING_PERIOD_ID,
            COURSE_NAME, GRADE, IS_COURSE, GRADE_SLOT, SCHOOL_ID);

    private Map<String, AverageCalculatorFactory> m_calculatorFactories;
    private Map<String, GradeTerm> m_currentGradeTerms;
    private Map<String, GradeScale> m_gradeScales;
    private GradesManager m_gradesManager;
    private Collection<SisStudent> m_students;
    private Map<String, TermAverageCalculator> m_termCalculators;

    @Override
    protected void loadGrid(ReportDataGrid grid) {
        Criteria criteria = new Criteria();
        criteria.addEqualTo(StudentSchedule.REL_SCHEDULE + "." +
                Schedule.REL_ACTIVE_SCHOOL_SCHEDULE_CONTEXTS + "." +
                SchoolScheduleContext.COL_DISTRICT_CONTEXT_OID, getCurrentContext().getOid());
        applySchoolFilterCriteria(criteria, StudentSchedule.REL_SCHEDULE + "." + Schedule.REL_SCHOOL);

        QueryByCriteria query = new QueryByCriteria(StudentSchedule.class, criteria);
        query.addOrderByAscending(StudentSchedule.REL_STUDENT + "." + Student.COL_LOCAL_ID);
        query.addOrderByAscending(StudentSchedule.REL_SECTION + "." + Section.COL_COURSE_VIEW);        
        
        QueryIterator<StudentSchedule> iterator = getBroker().getIteratorByQuery(query);
        try {
            while (iterator.hasNext()) {
                StudentSchedule studentSchedule = iterator.next();
                try {
                    appendGradeInformation(grid, studentSchedule);
                } catch (Exception e) {
                    // Log the exception and continue with the next student
                    System.err.println(
                            "Error processing student OID " + studentSchedule.getStudentOid() + ": " + e.getMessage());
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            // Log any exceptions that occur outside of the per-iteration try-catch
            System.err.println("Error in loadGrid method: " + e.getMessage());
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

        Collection<GradeTerm> terms = m_gradesManager.getCurrentGradeTerms(section.getSchedule().getSchool().getOid(),
                true);
        for (GradeTerm term : terms) {
            if (term != null) {
                // Generate a unique key for the calculator
                //String termKey = getCalculatorKey(section, term.getOid() + studentSchedule.getStudentOid());

                // Retrieve or create a calculator for this student and term
                //TermAverageCalculator termCalculator = m_termCalculators.get(termKey);
                //if (termCalculator == null) {
                 TermAverageCalculator termCalculator = (TermAverageCalculator) calculatorFactory.getTermAverageCalculator(
                            term,                            
                            m_students // Pass all students                            
                    );
                  //  m_termCalculators.put(termKey, termCalculator);
                //}

                // Use getAverageNumeric to get the numeric grade                
                
                Double numericGrade = termCalculator.getAverageNumeric(studentSchedule.getStudentOid());
                if (numericGrade != null) {
                    // Format the numeric grade according to your scale
                    DecimalFormat df = new DecimalFormat("0.##");
                    String grade = df.format(numericGrade);
                    grid.append();
                    setGradeInformation(grid, studentSchedule, term, grade);
                }
            }
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
        int averageMode = 1;

        SisStaff primaryStaff = section.getPrimaryStaff();
        if (primaryStaff != null) {
            SisUser staffUser = primaryStaff.getPerson().getUser(getBroker());

            if (staffUser != null) {
                /*
                 * Load the teacher's grading preferences and get an AverageCalculatorFactory
                 * for
                 * this student
                 */
                PreferenceSet parentPreferenceSet = PreferenceManager
                        .getPreferenceSet(section.getSchedule().getSchool());
                try {
                    decimals = Integer.parseInt(PreferenceManager.getPreferenceValue(staffUser, parentPreferenceSet,
                            SisPreferenceConstants.GRADES_AVERAGE_DECIMALS));
                } catch (NumberFormatException ex) {
                    // Do Nothing.
                }
                averageMode = GradebookManager.getEffectiveAverageMode(section.getOid(), staffUser, parentPreferenceSet,
                        getBroker());

                String gradeScaleKey = getCalculatorKey(section, section.getPrimaryStaffOid());
                gradeScale = m_gradeScales.get(gradeScaleKey);
                if (gradeScale == null) {
                    String gradeScaleOid = PreferenceManager.getPreferenceValue(staffUser, parentPreferenceSet,
                            SisPreferenceConstants.GRADEBOOK_AVERAGES_GRADESCALE);
                    gradeScale = (GradeScale) getBroker().getBeanByOid(GradeScale.class, gradeScaleOid);
                }
            }
        }

        AverageCalculatorFactory calculatorFactory = new AverageCalculatorFactory((MasterSchedule) section,
                decimals,
                m_gradesManager,
                gradeScale, // null,
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
     * @param section
     * @param secondaryKey this value could be a category OID or a term OID
     *
     * @return String
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
        m_currentGradeTerms = new HashMap<>(2048);
        m_gradeScales = new HashMap<String, GradeScale>();
        m_gradesManager = new GradesManager(getBroker());
        m_termCalculators = new HashMap<String, TermAverageCalculator>();
        m_calculatorFactories = new HashMap<String, AverageCalculatorFactory>();

        loadStudents();
    }

    private void loadStudents() {
        Criteria criteria = new Criteria();
        criteria.addEqualTo(SisStudent.COL_ENROLLMENT_STATUS, PreferenceManager.getPreferenceValue(getOrganization(),
                SystemPreferenceDefinition.STUDENT_ACTIVE_CODE));
        applySchoolFilterCriteria(criteria, SisStudent.REL_SCHOOL);

        QueryByCriteria query = new QueryByCriteria(SisStudent.class, criteria);
        m_students = getBroker().getCollectionByQuery(query);
    }

    private void setGradeInformation(ReportDataGrid grid, StudentSchedule studentSchedule, GradeTerm term,
            String gradeValue) {
        grid.set(STUDENT_ID, studentSchedule.getStudent().getLocalId());
        grid.set(SECTION_ID, studentSchedule.getSectionOid());
        grid.set(COURSE_NAME, studentSchedule.getSection().getDescription());
        grid.set(GRADE, gradeValue);
        grid.set(IS_COURSE, Boolean.TRUE.toString());
        grid.set(GRADE_SLOT, term.getGradeTermId());

        SisSchool school = studentSchedule.getSection().getSchedule().getSchool();
        grid.set(MARKING_PERIOD_ID, getMarkingPeriodId(school, term));
        grid.set(SCHOOL_ID, school.getSchoolId());
    }
}
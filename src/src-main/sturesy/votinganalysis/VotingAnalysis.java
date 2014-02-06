/*
 * StuReSy - Student Response System
 * Copyright (C) 2012-2013  StuReSy-Team
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package sturesy.votinganalysis;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;

import org.apache.commons.collections.CollectionUtils;

import sturesy.core.Controller;
import sturesy.core.GenericComparator;
import sturesy.core.Localize;
import sturesy.core.backend.services.crud.VotingAnalysisCRUDService;
import sturesy.core.ui.UneditableTableModel;
import sturesy.items.QuestionModel;
import sturesy.items.QuestionSet;
import sturesy.items.Vote;
import sturesy.util.Settings;
import sturesy.util.ValidVotePredicate;
import sturesy.voting.VotingEvaluationController;
import sturesy.votinganalysis.gui.VotingAnalysisUI;

/**
 * Class for Analysing Votings, in different Visual Diagrams
 * 
 * @author w.posdorfer
 * 
 */
public class VotingAnalysis implements Controller
{

    private static final String DELTA_STRING = "\u0394";

    private static final String LABEL_MEDIAN_RESSOURCE_KEY = "label.median";

    private static final String LABEL_MEAN_RESSOURCE_KEY = "label.mean";

    private VotingAnalysisUI _frame;

    private QuestionSet _questionSet;

    private int _currentQuestion;

    private VotingEvaluationController _evalPanel;

    private TimeChart _timeChart;

    private final Map<Integer, Set<Vote>> _votes;

    private VotingAnalysisCRUDService _votingAnalysisCRUDService;

    public VotingAnalysis(QuestionSet qset, Map<Integer, Set<Vote>> votes)
    {
        _votingAnalysisCRUDService = new VotingAnalysisCRUDService();
        _questionSet = qset;
        _votes = votes;
        filter(_votes);

        _evalPanel = new VotingEvaluationController();
        _evalPanel.setCurrentQuestion(_questionSet.getIndex(_currentQuestion));
        _evalPanel.setAnswerVisible(true);
        _timeChart = new TimeChart(_evalPanel.getPanel().getBackground());
        _frame = new VotingAnalysisUI(_questionSet.size(), _evalPanel.getPanel(), _timeChart.getPanel());

        init();
    }

    public VotingAnalysis(QuestionSet questionSet, Map<Integer, Set<Vote>> votes,
            VotingAnalysisCRUDService votingAnalysisCRUDService, VotingEvaluationController evalPanel, VotingAnalysisUI ui,
            TimeChart timeChart)
    {
        _votingAnalysisCRUDService = votingAnalysisCRUDService;
        _votes = votes;
        _questionSet = questionSet;
        _evalPanel = evalPanel;
        _evalPanel.setCurrentQuestion(_questionSet.getIndex(_currentQuestion));
        _evalPanel.setAnswerVisible(true);
        _frame = ui;
        _timeChart = timeChart;
        init();
    }

    private void init()
    {
        registerListeners();

        updateGuiComponents();
    }

    protected void previousButtonAction()
    {
        boolean hasPreviousAction = _currentQuestion > 0;
        if (hasPreviousAction)
        {
            _currentQuestion--;
        }
        updateGuiComponents();

    }

    protected void nextButtonAction()
    {
        boolean hasNextQuestion = _currentQuestion < _questionSet.size() - 1;
        if (hasNextQuestion)
        {
            _currentQuestion++;
        }
        updateGuiComponents();

    }

    private void updateGuiComponents()
    {
        _frame.setLabelText(_currentQuestion + 1, _questionSet.size());
        QuestionModel questionModel = _questionSet.getIndex(_currentQuestion);
        
        
        _evalPanel.setCurrentQuestion(questionModel);
        
        Set<Vote> votesToDisplay = _votes.get(_currentQuestion);
        String question = questionModel.getQuestion();
        List<String> answers = questionModel.getAnswers();
        Object[][] tablevalues = null;
        if (votesToDisplay != null)
        {
            tablevalues = createVoteTableValues(questionModel, votesToDisplay, question, answers);
        }
        else
        {
            _evalPanel.applyVotesToChart(new HashSet<Vote>(), questionModel);
            _frame.setArithmeticMeanText(buildTimeArithmeticMeanText(0));
            _frame.setMedianText(buildTimeMedianMean(0));
            _timeChart.applyVotesToChart(new HashSet<Vote>());
        }
        UneditableTableModel tablemodel = new UneditableTableModel(tablevalues, new String[] { "ID",
                Localize.getString("label.votes"), DELTA_STRING + " in s" });
        TableRowSorter<TableModel> tablerrowsorter = createConfiguredTableRowSorter(tablemodel);
        _frame.setTableModel(tablemodel, tablerrowsorter);

    }

    private TableRowSorter<TableModel> createConfiguredTableRowSorter(UneditableTableModel tablemodel)
    {
        TableRowSorter<TableModel> tablerrowsorter = new TableRowSorter<TableModel>(tablemodel);
        tablerrowsorter.setComparator(0, new GenericComparator<String>());
        tablerrowsorter.setComparator(1, new GenericComparator<String>());
        tablerrowsorter.setComparator(2, new GenericComparator<Integer>());
        return tablerrowsorter;
    }

    private Object[][] createVoteTableValues(QuestionModel questionModel, Set<Vote> votesToDisplay, String question,
            List<String> answers)
    {
        _evalPanel.applyVotesToChart(votesToDisplay, questionModel);
        _timeChart.applyVotesToChart(votesToDisplay);

        setAverageTextsOnWidget(votesToDisplay);

        Vote[] votes = votesToDisplay.toArray(new Vote[0]);
        Object[][] tablevalues = new Object[votes.length][3];

        for (int i = 0; i < votes.length; i++)
        {
            final Vote vote = votes[i];
            if (vote.getVote() < questionModel.getAnswerSize())
            {
                tablevalues[i][0] = vote.getGuid();
                tablevalues[i][1] = "" + (char) ('A' + vote.getVote());
                tablevalues[i][2] = vote.getTimeDiff() / 1000;
            }
        }
        return tablevalues;
    }

    private void setAverageTextsOnWidget(Set<Vote> votesToDisplay)
    {
        VoteAverage average = new VoteAverage(votesToDisplay);
        double timeArithmeticMean = average.getTimeArithmeticMean();
        final double timeMedian = average.getTimeMedian();
        _frame.setArithmeticMeanText(buildTimeArithmeticMeanText(timeArithmeticMean));
        _frame.setMedianText(buildTimeMedianMean(timeMedian));
    }

    private String buildTimeMedianMean(final double timeMedian)
    {
        return Localize.getString(LABEL_MEDIAN_RESSOURCE_KEY, timeMedian);
    }

    private String buildTimeArithmeticMeanText(double timeArithmeticMean)
    {
        return Localize.getString(LABEL_MEAN_RESSOURCE_KEY, timeArithmeticMean);
    }

    private void exportCSV()
    {

        Set<Vote> votes = _votes.get(_currentQuestion);
        File file = _frame.acceptSaveFromUser();
        String result = _votingAnalysisCRUDService.saveVotingResult(votes, file);
        if (result != null)
        {
            _frame.showMessageWindowError(result);
        }
    }

    /**
     * Filters all Votes which are not actually matching the associated question
     * 
     * @param votes
     */
    private void filter(Map<Integer, Set<Vote>> votes)
    {
        for (Integer index : votes.keySet())
        {
            int upperbound = _questionSet.getIndex(index).getAnswerSize();
            CollectionUtils.filter(votes.get(index), new ValidVotePredicate(upperbound));
        }
    }

    public VotingAnalysisUI getFrame()
    {
        return _frame;
    }

    @Override
    public void displayController(Component relativeTo, WindowListener listener)
    {
        Settings settings = Settings.getInstance();
        Dimension d = settings.getDimension(Settings.EVALUATEWINDOWSIZE);
        getFrame().setSize(d);
        getFrame().addWindowListener(listener);
        getFrame().setLocationRelativeTo(relativeTo);
        getFrame().setVisible(true);
    }

    /**
     * Register various Listeners on the gui-elements
     */
    private void registerListeners()
    {
        _frame.getNextButton().addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                nextButtonAction();
            }
        });
        _frame.getPreviousButton().addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                previousButtonAction();
            }
        });
        _frame.getExportCSV().addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                exportCSV();
            }
        });
        _frame.addWindowListener(new WindowAdapter()
        {
            public void windowClosing(WindowEvent e)
            {
                Settings.getInstance().setProperty(Settings.EVALUATEWINDOWSIZE, _frame.getSize());
            }
        });
    }

}
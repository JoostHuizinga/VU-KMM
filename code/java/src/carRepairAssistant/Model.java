package carRepairAssistant;

import java.util.ArrayList;
import java.util.LinkedList;
import jess.Rete;
import jess.JessException;
import jess.QueryResult;
import jess.WorkingMemoryMarker;
import jess.RU;
import java.util.List;
import java.util.Iterator;

/**
 * The model of the car repair assistent. 
 *
 * @author Tycho and Joost
 */
class Model {
    private Rete jess;
    private Control control;

    Model() {
        //Initialize jess
        jess = new Rete();
    }

    void setControl(Control control) {
        this.control = control;
    }

    void setup() throws JessException {
        jess.batch("jess/engine/run-from-java.jess");
        jess.reset();
    }

    /**
     * Asserts a complaint. A complaint is always a positive observation,
     * something is broken.
     *
     * @param c The observable that was observed a a complaint.
     */
    void assertComplaint(Observable c) throws JessException {
        String fact = "(complaint " + c.id() + " TRUE)";
        jess.assertString(fact);
    }

    /**
     * Asserts a finding (result of an observation) to positive or negative.
     *
     * @param f The observable that was observed.
     * @param result The result of the observation.
     *      <code>true</code> if the observation was positive,
     *      <code>false</code> if the observation was negative.
     */
    void assertFinding(Observable f, boolean result) throws JessException {
        String fact = "(observed " + f.id();
        if (result) {
            fact = fact + " TRUE)";
        } else {
            fact = fact + " FALSE)";
        }
        jess.assertString(fact);
        jess.run();
    }

    /**
     * Asserts that a certain hypothesis is not to be considered, it's impossible.
     * This can happen if the hypothesis was tested, for example a car repair
     * was made.
     *
     * @param h The hypothesis that is impossible
     */
    void assertImpossible(Hypothesis h) throws JessException {
        for (int i=0; i<h.hypothesisList.size(); i++) {
            jess.assertString(
                "(impossible " +
                    h.hypothesisList.get(i).id() + " " +
                    h.hypothesisList.get(i).stateId() +
                ")"
            );
        }
        jess.run();
    }

    /**
     * Gets the observables for a hypothesis that could falsify it.
     *
     * @return A list of the observables. An empty list if there are no
     *      observables for the hypothesis.
     */
    List<Observable> observables(Hypothesis h) throws JessException {
        List<Observable> result = new ArrayList<Observable>();

        test(h);
        jess.QueryResult observables = jess.runQueryStar("search-observable", new jess.ValueVector());
        while (observables.next()){
            result.add(new Observable(observables.getString("observable"), observables.getString("name")));
        }
        
        return result;
    }

    /**
     * Gets all possible hypothesis.
     *
     * @return A list with all the possible hypothesis.
     */
    List<Hypothesis> allHypothesis() throws JessException {
        List<Hypothesis> result = new ArrayList<Hypothesis>();

        //Runs the jess engine and queries for all basic hypothesis
        jess.run();
        jess.QueryResult hypothesis = jess.runQueryStar("search-hypothesis", new jess.ValueVector());

        //Adds the basic hypothesis to the result array
        while (hypothesis.next()) {
            result.add(new Hypothesis(hypothesis.getString("component"),
                    hypothesis.getString("name"),
                    hypothesis.getString("state"),
                    hypothesis.getString("stateName"),
                    this));
        }
        
        //Expands the basic hypothesis to composed hypothesis where necessary
        result = expandHypothesis(result);

        return result;
    }

    /**
     * Gets all the complaints that are likely. 
     *
     * @return A list of the likely complaints.
     */
    List<Observable> likelyComplaints() throws JessException {
        List<Observable> result = new ArrayList<Observable>();

        //Run the query
        QueryResult likely_complaints = jess.runQueryStar("search-likely-complaints", new jess.ValueVector());

        //Put the results in an array list
        while (likely_complaints.next()){
            result.add(new Observable(likely_complaints.getString("observable"), likely_complaints.getString("name")));
        }

        return result;
    }

    /**
     * Asserts a hypothesis and stores the results of that in Jess.
     * @param jess The supplied jess engine
     * @throws jess.JessException
     */
    void test(Hypothesis h) throws JessException {
        WorkingMemoryMarker beforeHypothesis = jess.mark();
        resetHypothesis();

        for (int i=0; i<h.hypothesisList.size(); i++) {
            jess.assertString(
                "(hypothesis " +
                    h.hypothesisList.get(i).id() + " " +
                    h.hypothesisList.get(i).stateId() +
                ")"
            );
        }
        jess.run();

        h.contradiction = !(jess.findFactByFact(new jess.Fact("contradiction", jess)) == null);
        h.directCause = !(jess.findFactByFact(new jess.Fact("direct-cause", jess)) == null);
        h.nrStateChanges = 0;
        jess.QueryResult components = jess.runQueryStar("components-in-state", new jess.ValueVector());

        while (components.next()) {
            h.nrStateChanges++;
        }
        
        jess.resetToMark(beforeHypothesis);
    }

    /**
     * Expands a list of basic hypothesis into composed hypothesis where necessary.
     * A basic hypothesis is an hypothesis which consists of only one component and its state.
     * A composed hypothesis is an hypothesis consisting of multiple components and their states.
     * It is necessary to expand a basic hypothesis when the hypothesis is not a direct cause.
     * 
     * E.G. 'Wire 16 is broken' is a basic hypothesis for 'left head light gives no light',
     * but assuming that ONLY 'wire 16 is broken' does not lead to 'left head light gives no light'
     * (it is not a direct cause).
     * Therefore 'wire 16 is broken' has to be expanded to, for example
     * 'wire 16 is broken and light switch is broken'.
     *
     * Note that most basic hypothesis are expanded into multiple composed hypothesis,
     * thus the returned list is often bigger then the inital list.
     *
     * @param allHypothesis A list of basic hypothesis which should be expanded.
     * @return A list of expanded hypothesis.
     * @throws jess.JessException
     */
    private List<Hypothesis> expandHypothesis(List<Hypothesis> allHypothesis) throws JessException {
        List<Hypothesis> result = new ArrayList<Hypothesis>();
        List<Hypothesis> basicHypothesis = new ArrayList<Hypothesis>();
        LinkedList<Hypothesis> candidateHypothesis = new LinkedList<Hypothesis>();
        Hypothesis currentHypothesis;
        Hypothesis newHypothesis;

        //Check each hypothesis and create a list of hypothesis should be expanded
        for(int i=0; i<allHypothesis.size(); i++){
            currentHypothesis = allHypothesis.get(i);

            if(currentHypothesis.directCause()){
                //If the current hypothesis is a direct cause it does not need expanding
                //add it to result
                result.add(allHypothesis.get(i));
            } else {
                //If this hypothesis is not a direct cause then it needs expanding
                //Store the index of this hypothesis for optimalization purpose
                currentHypothesis.maxIndex = basicHypothesis.size();
                //Add the hypothesis to the list of basic hypothesis that will be used for expanding
                basicHypothesis.add(currentHypothesis);
                //Add the hypothesis to the queue of candidates for expanding
                candidateHypothesis.add(currentHypothesis); 
            }
        }

        //Expand the candidate hypothesis
        while(candidateHypothesis.peek() != null){
            //Get the first hypothesis
            currentHypothesis = candidateHypothesis.pop();
            for(int j=currentHypothesis.maxIndex; j<basicHypothesis.size(); j++){
                //For each basic hypothesis that we have not tried with this combination
                //Create a new composed hypothesis by expanding the candidate with the basic hypothesis
                newHypothesis = currentHypothesis.clone();
                newHypothesis.add(basicHypothesis.get(j));

                if(newHypothesis.directCause()){
                    //If the new hypothesis is a direct cause then a new composed hypothesis has been found
                    //Add it to the result
                    result.add(newHypothesis);
                } else {
                    //If the new hypothesis is not a direct cause then it might need further expanding.
                    //Check whether new information was gained with the composed hypothesis by
                    //checking whether the new hypothesis causes more state changes then
                    //the two previous hypothesis combined
                    if((currentHypothesis.nrStateChanges() + basicHypothesis.get(j).nrStateChanges() < newHypothesis.nrStateChanges())){
                        //If new information was gained then the new hypothesis
                        //should get further expanded
                        candidateHypothesis.add(newHypothesis);
                    }
                }
            }
        }

        return result;
    }

    /**
     * Retracts all hypothesis and all conclusions that relate to these hypothesis from the supplied jess enginge.
     * Facts related to hypothesis are contradictions and direct causes.
     * Also reverts all state changes.
     *
     * @throws jess.JessException
     */
    private void resetHypothesis() throws JessException {
        jess.Value nil = new jess.Value("nil", RU.SYMBOL);
        jess.Fact fact;

        Iterator<jess.Fact> facts = jess.listFacts();
        while(facts.hasNext()){
            fact = facts.next();
            //Reset all states to nill before trying a new hypothesis
            if (fact.getDeftemplate().getSlotIndex("state")>0){
                jess.modify(fact, "state", nil);
            }
            //Reset all could-be-observed to nil before trying a new hypothesis
            if (fact.getDeftemplate().getSlotIndex("could-be-observed")>0){
                jess.modify(fact, "could-be-observed", nil);
            }
            //Retract all old hypothesis
            if (fact.getName().equals("MAIN::hypothesis")){
                jess.retract(fact);
            }
            //Retract all old contradictions
            if (fact.getName().equals("MAIN::contradiction")){
                jess.retract(fact);
            }
            //Retract all old direct-causes
            if (fact.getName().equals("MAIN::direct-cause")){
                jess.retract(fact);
            }
        }
    }
}

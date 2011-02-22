package carRepairAssistant;

import java.util.ArrayList;
import java.util.List;
import jess.JessException;
import java.util.regex.Pattern;

/**
 * A hypothesis that consists of one or more components and their states.
 *
 * A basic hypothesis consists of one ComponentState. An composed hypothesis consists
 * of one or more ComponentsStates.
 *
 * @author Joost and Tycho
 */
public class Hypothesis {
    private List<ComponentState> componentList;
    private Model m;

    private int nrStateChanges;
    private boolean contradiction;
    private boolean directCause;
    private boolean testResultSet;
    // We assume there are observations left for this hypothesis until we
    // experience otherwise.
    private boolean possible = true;

    /**
     * Construct an empty hypothesis
     */
    Hypothesis(Model m){
        componentList = new ArrayList<ComponentState>();
        this.m = m;
    }

    Hypothesis(List<ComponentState> componentList, Model m){
        this.componentList = componentList;
        this.m = m;
    }

    Hypothesis(Hypothesis hypothesis, Model m){
        componentList = new ArrayList<ComponentState>();
        for(int i =0; i<hypothesis.size();i++){
            componentList.add(hypothesis.get(i));
        }
        this.m = hypothesis.m;
    }

    Hypothesis(ComponentState component, Model m){
        componentList = new ArrayList<ComponentState>();
        componentList.add(component);
        this.m = m;
    }

    Hypothesis(String componentId, String componentName, String stateId, String stateName, Model m){
        componentList = new ArrayList<ComponentState>();
        componentList.add(new ComponentState(componentId, componentName, stateId, stateName));
        this.m = m;
    }



    /**
     * Finds extensions of this hypothesis.
     * It returns a list of hypothesis that contains this hypothesis
     * and that extends it with at most one basic hypothesis.
     *
     * If this hypothesis is an empty hypothesis then the result will be
     * a list of basic hypothesis.
     *
     * This function is used to generate the list of hypothesis that is shown to
     * the user.
     *
     * @param hypothesis The list being filtered and simplified
     * @return
     */
    public List<Hypothesis> filterSingleExtensions(List<Hypothesis> hypothesis){
        List<Hypothesis> result = new ArrayList<Hypothesis>();
        List<Hypothesis> temp;

        result = filterExtensions(hypothesis);
        result = simplifyHypothesis(result);

        //Remove the current hypothesis
        temp = this.split();
        for(int i=0; i<temp.size();i++){
            result.remove(temp.get(i));
        }

        //Add current hypothesis to all basic hypothesis
        for(int i=0; i<result.size();i++){
            result.get(i).add(this);
        }

        return result;
    }
    /*
     **************************************************************************
     * Transformations
     */
    
    /**
     * Splits a composed hypothesis into basic hypothesis
     * When the current hypothesis is already a basic hypothesis it just returns the current hypothesis
     * @return
     */
    public List<Hypothesis> split(){
        List<Hypothesis> result = new ArrayList<Hypothesis>();

         for(int i=0; i<componentList.size(); i++){
             result.add(new Hypothesis(componentList.get(i), m));
         }

        return result;
    }
    public Hypothesis clone() {
        return new Hypothesis(this, m);
    }

    /*
     **************************************************************************
     * Modifications
     */

    /**
     * Adds an other hypothesis to this hypothesis.
     * @param otherHypothesis The hypothesis to be added to this hypothesis
     */
    public void add(Hypothesis otherHypothesis){
        for(int i=0; i<otherHypothesis.size(); i++){
            componentList.add(otherHypothesis.get(i));
        }
        nrStateChanges = -1;
    }

    public void setTestResult(boolean contradiction, boolean directCause, int nrStateChanges) {
        this.contradiction = contradiction;
        this.directCause = directCause;
        this.nrStateChanges = nrStateChanges;
    }

    public void setImpossible() {
        possible = false;
    }

    /*
     **************************************************************************
     * Predicates
     */

    public boolean containsWire() {
        for (ComponentState c : componentList) {
            if (Pattern.matches(".*wire.*", c.id())) {
                return true;
            }
        }
        return false;
    }

    public boolean possible() {
        return possible;
    }

    /**
     * Indicates whether the other hypothesis is contained in the current hypothesis.
     * Note that an empty hypothesis is always contained in an other hypothesis.
     * @param otherHypothesis The hypothesis of which it is tested if it is contained whithin this hypothesis
     * @return Returns true if this hypothesis contains the other hypothesis
     */
    public boolean contains(Hypothesis otherHypothesis){
        for(int i=0; i<otherHypothesis.size(); i++){
            if(!componentList.contains(otherHypothesis.get(i))){
                return false;
            }
        }
        
        return true;
    }

    /**
     * Returns whether this hypothesis causes a contradiction whithin the supplied jess engine.
     * @param jess The supplied jess engine
     * @return Returns true if this hypothesis causes a contradiction
     * @throws jess.JessException
     */
    public boolean contradiction() throws JessException{
        if (!testResultSet) {
            m.test(this);
        }
        return contradiction;
    }

    /**
     * Returns whether this hypothesis is direct cause for the complaint in the supplied jess engine.
     * A direct cause means that this hypothesis will lead to the initial complaint whitout the need for additional information.
     * @param jess The supplied jess engine
     * @return Returns true if this hypothesis is a direct cause
     * @throws jess.JessException
     */
    public boolean directCause() throws JessException{
        if (!testResultSet) {
            m.test(this);
        }
        return directCause;
    }

    /**
     * Returns the number of state changes whithin the supplied model that is caused by assuming this hypothesis.
     *
     * @return Returns the number of state changes
     * @throws jess.JessException
     */
    public int nrStateChanges() throws JessException {
        if (nrStateChanges == -1){
            m.test(this);
        }
        return nrStateChanges;
    }

    /*
     **************************************************************************
     * Methods to use this as a list of ComponentStates
     */

    /**
     * Returns the component at index i within the hypothesis object
     * @param i The index of the component
     * @return The component at index i
     */
    public ComponentState get(int i) {
        return componentList.get(i);
    }

    /**
    * Returns the number of components in this hypothesis
    * @return the number of components
    */
    public int size(){
        return componentList.size();
    }

    /*
     **************************************************************************
     * Methods for efficient List membership
     */
    @Override
    public boolean equals(Object otherObject){
        boolean temp;

        if(otherObject == null)
            return false;
        if(otherObject.getClass() != this.getClass())
            return false;

        Hypothesis otherHypothesis = (Hypothesis) otherObject;
        
        if(componentList.size() != otherHypothesis.size())
            return false;

        for(int i=0; i<componentList.size(); i++){
            temp = false;
            for(int j=0; j<otherHypothesis.size(); j++){
                if(componentList.get(i).equals(otherHypothesis.get(j)))
                    temp = true;
            }

            if(!temp){
                return false;
            }
        }

        return true;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 67 * hash + (this.componentList != null ? this.componentList.hashCode() : 0);
        return hash;
    }

    /*
     **************************************************************************
     * Methods for interaction with Model
     */

    void assertH() throws JessException {
        for (ComponentState c : componentList) {
            m.assertComponentState(c);
        }

    }



    /*
     **************************************************************************
     * Private
     */

    /**
     * Takes a list of composed and basic hypothesis and returns a list of all
     * different basic hypothesis that are contained in these hypothesis.
     * @param componentList The list of hypothesis to be simplified
     * @return A list of basic hypothesis
     */
    private static List<Hypothesis> simplifyHypothesis(List<Hypothesis> hypothesis){
        List<Hypothesis> result = new ArrayList<Hypothesis>();
        List<Hypothesis> temp;

        //create an array of all basic hypothesis that are in the filtered hypothesis and that does not contain duplicates
        for(int i=0; i<hypothesis.size(); i++){
            //Split the hypothesis into its basic hypothesis
            temp = hypothesis.get(i).split();
            for(int j=0; j<temp.size(); j++){
                if(!result.contains(temp.get(j))){
                    //If the basic hypothesis is not yet contained in the result
                    //Add it to the result
                    result.add(temp.get(j));
                }
            }
        }

        return result;
    }


    /**
     * Takes a  a list of hypothesis. It then returns a list
     * of hypothesis that all contain the filter hypothesis and that does not
     * contain any hypothesis that was already tested.
     *
     * Note, the filter hypothesis might be an empty hypothesis in which case
     * this function only removes those hypothesis that are tested
     * @param filterHypothesis The hypothesis used to filter the hypothesis list
     * @param componentList The hypothesis list that is being filtered
     * @return A filtered hypothesis list
     */
    private List<Hypothesis> filterExtensions(List<Hypothesis> hypothesis){
        List<Hypothesis> result = new ArrayList<Hypothesis>();

        //find all hypothesis that contain the current hypothesis and are not yet tested
        for(int i=0; i<hypothesis.size(); i++){
            if (hypothesis.get(i).contains(this)) {
                //If this hypothesis contains the filter hypothesis and is not yet tested
                //Add it to the result
                result.add(hypothesis.get(i));
            }
        }

        return result;
    }
}

package org.ncgr.intermine.web;

/*
 * Copyright (C) 2002-2014 FlyMine
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  See the LICENSE file for more
 * information or http://www.gnu.org/copyleft/lesser.html.
 *
 */

import java.text.DecimalFormat;

/**
 * A Java class to store an expression value for an mRNA.
 *
 * @author Sam Hokin
 *
 */
public class ExpressionValue {
    
    // the sample name
    private String condition;

    // the sample number
    private Integer num;

    // the expression value
    private Double value;

    // the feature primaryId
    private String primaryId;

    DecimalFormat df = new DecimalFormat("#.##");

    /**
     * Constructor.
     * @param condition the experiment condition name
     * @param value the expression value
     * @param primaryId the feature's primaryId
     */
    public ExpressionValue(String condition, Integer num, Double value, String primaryId) {
        this.condition = condition;
        this.num = num;
        this.value = value;
        this.primaryId = primaryId;
    }

    /**
     * Default Constructor.
     */
    public ExpressionValue() {
        super();
    }

    /**
     * @return the value
     */
    public Double getValue() {
        return value;
    }

    /**
     * @return the sample name
     */
    public String getCondition() {
        return condition;
    }

    /**
     * @return the sample number
     */
    public Integer getNum() {
        return num;
    }

    /**
     * @return the feature primaryId
     */
    public String getPrimaryId() {
        return primaryId;
    }

    /**
     * @param primaryId the primaryId to set
     */
    public void setPrimaryId(String primaryId) {
        this.primaryId = primaryId;
    }

}

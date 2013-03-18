/*
 * Copyright (c) 2007-2012 The Broad Institute, Inc.
 * SOFTWARE COPYRIGHT NOTICE
 * This software and its documentation are the copyright of the Broad Institute, Inc. All rights are reserved.
 *
 * This software is supplied without any warranty or guaranteed support whatsoever. The Broad Institute is not responsible for its use, misuse, or functionality.
 *
 * This software is licensed under the terms of the GNU Lesser General Public License (LGPL),
 * Version 2.1 which is available at http://www.opensource.org/licenses/lgpl-2.1.php.
 */

package org.broad.igv.ui.event;

/**
 * Events corresponding to a change in viewed area (chromosome, position, and/or zoom).
 *
 * {@code Cause} derived events
 * should cause the data model (e.g. ReferenceFrame) to change their position, this will
 * typically be dispatched by a UI Component.
 *
 * {@code Result} derived events should be dispatched after objects have changed
 * their position, typically to tell UI elements they should repaint
 * User: jacob
 * Date: 2013-Jan-30
 */
public abstract class ViewChange {
    protected boolean recordHistory = false;

    public boolean recordHistory(){
        return this.recordHistory;
    }

    public void setRecordHistory(boolean recordHistory){
        this.recordHistory = recordHistory;
    }

    public static class Cause extends ViewChange{}

    public static class Result extends ViewChange{}

    /**
     * Event indicating that the zoom should change.
     */
    public static class ZoomCause extends Cause{
        //public final int oldZoom;
        public final int newZoom;
        //public final Object source;

        public ZoomCause(int newZoom){
            //this.oldZoom = oldZoom;
            this.newZoom = newZoom;
            //this.source = source;
        }
    }

    public static class ZoomResult extends Result{}

    public static class ChromosomeChangeCause extends Cause{

        public final Object source;
        public final String chrName;

        /**
         *
         * @param source The object which originated the chromosome change
         * @param chrName
         */
        public ChromosomeChangeCause(Object source, String chrName){
            this.source = source;
            this.chrName = chrName;
        }
    }

    public static class ChromosomeChangeResult extends Result{

        public final Object source;
        public final String chrName;

        public ChromosomeChangeResult(Object source, String chrName){
            this.source = source;
            this.chrName = chrName;
        }
    }

}

module org.igv {
    exports org.broad.igv;
    exports org.broad.igv.tools;
    exports org.broad.igv.ui;

    requires AbsoluteLayout;
//    requires ant; // XXX: I cannot see why this is required
    requires com.google.common;
    requires protobuf.java;
    requires commons.math3;
    requires gson;
    requires htsjdk;
    requires java.datatransfer;
    requires java.desktop;
    requires java.instrument;
    requires java.management;
    requires java.prefs;
    requires java.sql;
    requires java.xml;
    requires jdk.xml.dom;
    requires org.apache.logging.log4j;
    requires org.apache.logging.log4j.core;
    requires swing.layout;
    requires jide.common;
    requires org.apache.commons.io;
    requires goby.io;
    requires icb.utils;
    requires fastutil;
    requires org.apache.batik.svggen;
    requires org.apache.batik.dom;
    requires dsiutils;

    // AWS
    requires software.amazon.awssdk.core;
    requires software.amazon.awssdk.regions;
    requires software.amazon.awssdk.services.s3;
    requires software.amazon.awssdk.auth;
    requires software.amazon.awssdk.services.cognitoidentity;
    requires software.amazon.awssdk.http;
    requires software.amazon.awssdk.utils;
}

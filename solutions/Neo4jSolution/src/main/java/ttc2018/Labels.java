package ttc2018;

import org.neo4j.graphdb.Label;

public enum Labels implements Label {
    Submission,
    Post,
    Comment,
    User,
    // extra labels
    Dirty,
    Component,
    ;

    public static final Label[] PostLabelSet = new Label[]{Submission, Post};
    public static final Label[] CommentLabelSet = new Label[]{Submission, Comment};
}

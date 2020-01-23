package ttc2018;

import apoc.create.Create;
import apoc.path.PathExplorer;
import apoc.periodic.Periodic;
import apoc.refactor.GraphRefactoring;
import com.google.common.collect.ImmutableMap;
import org.neo4j.cypher.CypherExecutionException;
import org.neo4j.graphalgo.GetNodeFunc;
import org.neo4j.graphalgo.UnionFindProc;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.internal.kernel.api.exceptions.KernelException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

import static ttc2018.Labels.Comment;

public class SolutionQ2 extends Solution {

    private Tool tool;

    public SolutionQ2(String DataPath, String toolName) throws IOException, InterruptedException {
        super(DataPath);

        tool = Tool.valueOf(toolName);
    }

    public enum Tool {
        Neo4jSolution_overlay_graph(true, false),
        Neo4jSolution_explicit_component(false, true),
        Neo4jSolution(false, true), // previously Neo4jSolution_explicit_component_periodic
        Neo4jSolution_explicit_component_algo(false, true),
        ;

        final boolean maintainOverlayGraph;
        final boolean maintainExplicitComponent;

        Tool(boolean maintainOverlayGraph, boolean maintainExplicitComponent) {
            this.maintainOverlayGraph = maintainOverlayGraph;
            this.maintainExplicitComponent = maintainExplicitComponent;
        }
    }

    @Override
    protected void initializeDb() throws KernelException {
        super.initializeDb();

        if (tool.maintainExplicitComponent)
            registerProcedure(graphDb, GraphRefactoring.class);

        switch (tool) {
            case Neo4jSolution_explicit_component_algo:
                registerProcedure(graphDb, UnionFindProc.class, GetNodeFunc.class);
                break;
            case Neo4jSolution:
                registerProcedure(graphDb, Create.class, Periodic.class, PathExplorer.class);
                break;
        }
    }

    @Override
    protected void addConstraintsAndIndicesInTx(GraphDatabaseService dbConnection) {
        super.addConstraintsAndIndicesInTx(dbConnection);

        dbConnection.schema()
                .indexFor(Comment)
                .on(SUBMISSION_SCORE_PROPERTY)
                .create();

        // note: cannot create index on commentId property of FRIEND_WHO_LIKES_COMMENT edge
    }

    @Override
    public String Initial() {
        switch (tool) {
            case Neo4jSolution_overlay_graph:
                runVoidQuery(Query.Q2_INITIAL_OVERLAY_GRAPH);

                runVoidQuery(Query.Q2_INITIAL_SCORE);
                break;
            case Neo4jSolution_explicit_component:
                runVoidQuery(Query.Q2_INITIAL_OVERLAY_GRAPH);

                runVoidQuery(Query.Q2_INITIAL_COMPONENTS_AND_SCORE);
                runVoidQuery(Query.Q2_INITIAL_ZERO_SCORE);
                break;
            case Neo4jSolution_explicit_component_algo:
                runVoidQuery(Query.Q2_INITIAL_COMPONENTS_AND_SCORE_ALGO);
                runVoidQuery(Query.Q2_INITIAL_ZERO_SCORE);
                break;
            case Neo4jSolution:
                runVoidQuery(Query.Q2_INITIAL_DYNAMIC_LIKES_LABELS);

                Map batchErrors = (Map) Query.Q2_INITIAL_COMPONENTS_PERIODIC.execute(this, Collections.emptyMap())
                        .columnAs("batchErrors")
                        .stream().collect(Collectors.toList())
                        .get(0);
                if (!batchErrors.isEmpty())
                    throw new CypherExecutionException(batchErrors.toString(), new Exception());

                runVoidQuery(Query.Q2_INITIAL_SCORE_FROM_EXPLICIT_COMPONENTS);
                break;
            default:
                throw new IllegalArgumentException();
        }
        String result = runReadQuery(Query.Q2_RETRIEVE);

        return result;
    }

    @Override
    protected void afterNewComment(Node comment, Node submitter, Node previousSubmission, Node rootPost) {
        super.afterNewComment(comment, submitter, previousSubmission, rootPost);

        comment.setProperty(SUBMISSION_SCORE_PROPERTY, SUBMISSION_SCORE_DEFAULT);
    }

    @Override
    protected Relationship addFriendEdge(String[] line) {
        Relationship friendEdge = super.addFriendEdge(line);
        newFriendEdges.add(friendEdge);

        if (tool.maintainExplicitComponent) {
            runVoidQuery(Query.Q2_MERGE_COMPONENTS_AFTER_FRIEND_EDGE, ImmutableMap.of("friendEdge", friendEdge));
        }

        return friendEdge;
    }

    @Override
    protected Relationship addLikesEdge(String[] line) {
        Relationship likesEdge = super.addLikesEdge(line);
        newLikesEdges.add(likesEdge);

        if (tool.maintainExplicitComponent) {
            runVoidQuery(Query.Q2_MERGE_COMPONENTS_AFTER_LIKES_EDGE, ImmutableMap.of("likesEdge", likesEdge));
        }

        return likesEdge;
    }

    private ArrayList<Relationship> newFriendEdges;
    private ArrayList<Relationship> newLikesEdges;

    @Override
    public String Update(File changes) {
        newFriendEdges = new ArrayList<>();
        newLikesEdges = new ArrayList<>();

        beforeUpdate(changes);

        if (tool.maintainOverlayGraph) {
            if (!newFriendEdges.isEmpty())
                runVoidQuery(Query.Q2_UPDATE_OVERLAY_GRAPH_FRIEND_EDGE, ImmutableMap.of("friendEdges", newFriendEdges));
            if (!newLikesEdges.isEmpty())
                runVoidQuery(Query.Q2_UPDATE_OVERLAY_GRAPH_LIKES_EDGE, ImmutableMap.of("likesEdges", newLikesEdges));

            runVoidQuery(Query.Q2_RECALCULATE_SCORE);
        }
        String result = runReadQuery(Query.Q2_RETRIEVE);

        afterUpdate();

        return result;
    }
}

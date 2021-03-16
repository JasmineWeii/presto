/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.facebook.presto.sql.analyzer;

import com.facebook.presto.sql.tree.DefaultTraversalVisitor;
import com.facebook.presto.sql.tree.Join;
import com.facebook.presto.sql.tree.JoinCriteria;
import com.facebook.presto.sql.tree.JoinOn;
import com.facebook.presto.sql.tree.LogicalBinaryExpression;
import com.facebook.presto.sql.tree.Node;

import java.util.HashSet;
import java.util.Set;

import static com.facebook.presto.sql.analyzer.SemanticErrorCode.NOT_SUPPORTED;
import static java.util.Objects.requireNonNull;

// TODO: Add more cases https://github.com/prestodb/presto/issues/16032
public class MaterializedViewPlanValidator
        extends DefaultTraversalVisitor<Void, MaterializedViewPlanValidator.MaterializedViewPlanValidatorContext>
{
    private final Node query;

    public MaterializedViewPlanValidator(Node query)
    {
        this.query = requireNonNull(query, "query is null");
    }

    @Override
    protected Void visitJoin(Join node, MaterializedViewPlanValidatorContext context)
    {
        context.getJoinNodes().add(node);
        if (context.getJoinNodes().size() > 1) {
            throw new SemanticException(NOT_SUPPORTED, query, "More than one join in materialized view is not supported yet.");
        }

        if (!node.getType().equals(Join.Type.INNER)) {
            throw new SemanticException(NOT_SUPPORTED, node, "Only inner join is supported for materialized view.");
        }
        if (!node.getCriteria().isPresent()) {
            throw new SemanticException(NOT_SUPPORTED, node, "Join with no criteria is not supported for materialized view.");
        }

        JoinCriteria joinCriteria = node.getCriteria().get();
        if (!(joinCriteria instanceof JoinOn)) {
            throw new SemanticException(NOT_SUPPORTED, node, "Only join-on is supported for materialized view.");
        }

        context.setProcessingJoinNode(true);
        process(((JoinOn) joinCriteria).getExpression(), context);
        context.setProcessingJoinNode(false);
        return null;
    }

    @Override
    protected Void visitLogicalBinaryExpression(LogicalBinaryExpression node, MaterializedViewPlanValidatorContext context)
    {
        if (!context.isProcessingJoinNode()) {
            return null;
        }

        // TODO: It should only support equi join case https://github.com/prestodb/presto/issues/16033
        if (!node.getOperator().equals(LogicalBinaryExpression.Operator.AND)) {
            throw new SemanticException(NOT_SUPPORTED, node, "Only AND operator is supported for join criteria for materialized view.");
        }
        return super.visitLogicalBinaryExpression(node, context);
    }

    protected static final class MaterializedViewPlanValidatorContext
    {
        private final Set<Join> joinNodes;
        private boolean isProcessingJoinNode;

        public MaterializedViewPlanValidatorContext()
        {
            joinNodes = new HashSet<>();
        }

        public Set<Join> getJoinNodes()
        {
            return joinNodes;
        }

        public void setProcessingJoinNode(boolean processingJoinNode)
        {
            isProcessingJoinNode = processingJoinNode;
        }

        public boolean isProcessingJoinNode()
        {
            return isProcessingJoinNode;
        }
    }
}

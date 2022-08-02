package de.innfactory.smithy4play.protocols;


import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.node.ObjectNode;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.AnnotationTrait;
import software.amazon.smithy.model.traits.AbstractTrait;

public class RestProtocolTrait extends AnnotationTrait {

    public static ShapeId ID = ShapeId.from("smithy4play.api#restProtocol");

    public RestProtocolTrait(ObjectNode node) {
        super(ID, node);
    }

    public RestProtocolTrait() {
        super(ID, Node.objectNode());
    }

    public static final class Provider extends AbstractTrait.Provider {
        public Provider() {
            super(ID);
        }

        @Override
        public RestProtocolTrait createTrait(ShapeId target, Node node) {
            return new RestProtocolTrait(node.expectObjectNode());
        }
    }
}

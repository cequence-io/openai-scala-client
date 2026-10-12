package io.cequence.openaiscala.service

import play.api.libs.json.JsValue

import scala.quoted.*

/**
 * Scala 3: the inferred schema of `T` - an instance of your own [[JsonSchemaOf]] in scope, else
 * the schema derived at compile time. Unlike [[JsonSchemaOf.derived]], a type the derivation
 * does not support (or an abstract one) infers nothing instead of failing to compile.
 */
trait InferredJsonSchemaDerivation {

  inline given inferred[T]: InferredJsonSchema[T] = ${ InferredJsonSchemaMacros.inferredImpl[T] }
}

/** Internal - the macro behind the Scala 3 inference. */
object InferredJsonSchemaMacros {

  def inferredImpl[T: Type](using q: Quotes): Expr[InferredJsonSchema[T]] = {
    import q.reflect.*

    val tpe = TypeRepr.of[T].dealias
    val typeName =
      if (tpe.typeSymbol.isClassDef) tpe.typeSymbol.name else InferredJsonSchema.DefaultName

    // the derived instance is skipped: it fails to compile for a type it cannot derive
    def isDerived(tree: Tree): Boolean =
      tree match {
        case Inlined(Some(call), _, _) => isDerived(call)
        case _ =>
          tree.symbol.name == "derived" && tree.symbol.owner.name == "JsonSchemaOfDerivation"
      }

    if (tpe <:< TypeRepr.of[JsValue]) '{ InferredJsonSchema.none[T] }
    else
      Implicits.search(TypeRepr.of[JsonSchemaOf[T]]) match {
        // named after the instance, else `response` - as on Scala 2
        case found: ImplicitSearchSuccess if !isDerived(found.tree) =>
          val schemaOf = found.tree.asExprOf[JsonSchemaOf[T]]
          '{ InferredJsonSchema.fromSchemaOf[T]($schemaOf) }

        case _ =>
          JsonSchemaMacros.softShapeOf[T] match {
            case Some(shape) => '{ InferredJsonSchema.fromShape[T](${ Expr(typeName) }, $shape) }
            case None        => '{ InferredJsonSchema.none[T] }
          }
      }
  }
}

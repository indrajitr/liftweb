package net.liftweb.json

/*
 * Copyright 2009 WorldWide Conferencing, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 */

import java.lang.reflect.{Constructor => JConstructor, Type}
import java.util.Date
import scala.reflect.Manifest
import JsonAST._

/** Function to extract values from JSON AST using case classes.
 *
 *  FIXME: Add support to extract List of values too.
 *
 *  See: ExtractionExamples.scala
 */
object Extraction {
  import Meta._
  import Meta.Reflection._

  def extract[A](json: JValue)(implicit formats: Formats, mf: Manifest[A]): A = 
    try {
      extract0(json, formats, mf)
    } catch {
      case e: MappingException => throw e
      case e: Exception => throw new MappingException("unknown error", e)
    }

  def decompose(a: Any)(implicit formats: Formats): JValue = {
    def prependTypeHint(clazz: Class[_], o: JObject) = 
      JField("jsonClass", JString(formats.typeHints.hintFor(clazz))) ++ o

    def mkObject(clazz: Class[_], fields: List[JField]) = formats.typeHints.containsHint_?(clazz) match {
      case true => prependTypeHint(clazz, JObject(fields))
      case false => JObject(fields)
    }
 
    val serializer = formats.typeHints.serialize
    val any = a.asInstanceOf[AnyRef]
    if (!serializer.isDefinedAt(a)) {
      any match {
        case null => JNull
        case x if primitive_?(x.getClass) => primitive2jvalue(x)(formats)
        case x: List[_] => JArray(x map decompose)
        case x: Option[_] => decompose(x getOrElse JNothing)
        case x => 
          x.getClass.getDeclaredFields.filter(!static_?(_)).toList.map { f => 
            f.setAccessible(true)
            JField(unmangleName(f), decompose(f get x))
          } match {
            case Nil => JNothing
            case fields => mkObject(x.getClass, fields)
          }
      }
    } else prependTypeHint(any.getClass, serializer(any))
  }

  private def extract0[A](json: JValue, formats: Formats, mf: Manifest[A]): A = {
    val mapping = mappingOf(mf.erasure)

    def newInstance(targetType: Class[_], args: => List[Any], json: JValue) = {
      def instantiate(constructor: JConstructor[_], args: List[Any]) = 
        try {
          constructor.newInstance(args.map(_.asInstanceOf[AnyRef]).toArray: _*)
        } catch {
          case e @ (_:IllegalArgumentException | _:InstantiationException) => 
            fail("Parsed JSON values do not match with class constructor\nargs=" + args.mkString(",") + 
                 "\narg types=" + args.map(arg => if (arg != null) arg.asInstanceOf[AnyRef].getClass.getName else "null").mkString(",") + 
                 "\nconstructor=" + constructor)
        }

      def instantiateUsingTypeHint(typeHint: String, fields: List[JField]) = {
        val obj = JObject(fields)
        val deserializer = formats.typeHints.deserialize
        if (!deserializer.isDefinedAt(typeHint, obj)) {
          val concreteClass = formats.typeHints.classFor(typeHint) getOrElse fail("Do not know how to deserialize '" + typeHint + "'")
          build(obj, mappingOf(concreteClass), Nil)(0)
        } else deserializer(typeHint, obj)
      }

      json match {
        case JObject(JField("jsonClass", JString(t)) :: xs) => instantiateUsingTypeHint(t, xs)
        case JField(_, JObject(JField("jsonClass", JString(t)) :: xs)) => instantiateUsingTypeHint(t, xs)
        case _ => instantiate(primaryConstructorOf(targetType), args)
      }
    }

    def newPrimitive(elementType: Class[_], elem: JValue) = convert(elem, elementType, formats)

    def build(root: JValue, mapping: Mapping, argStack: List[Any]): List[Any] = mapping match {
      case Value(path, targetType) => convert(fieldValue(root, path), targetType, formats) :: argStack
      case Constructor(path, targetType, args) => 
        val newRoot = path match {
          case Some(p) => root \ p
          case None => root
        }
        newInstance(targetType, args.flatMap(build(newRoot, _, argStack)), newRoot) :: Nil
      case ListConstructor(path, targetType, args) => 
        val arr = fieldValue(root, path).asInstanceOf[JArray]
        arr.arr.map(elem => newInstance(targetType, args.flatMap(build(elem, _, argStack)), elem)) :: argStack
      case ListOfPrimitives(path, elementType) =>
        val arr = fieldValue(root, path).asInstanceOf[JArray]
        arr.arr.map(elem => newPrimitive(elementType, elem)) :: argStack
      case Optional(m) =>
        // FIXME Remove this try-catch.
        try { 
          val opt = build(root, m, argStack) 
          (opt(0) match {
            case null => None
            case x => Some(x)
          }) :: argStack
        } catch {
          case e: MappingException => None :: argStack
        }
    }

    def fieldValue(json: JValue, path: String) = (json \ path) match {
      case JField(_, value) => value
      case x => fail("Expected JField but got " + x + ", json='" + json + "', path='" + path + "'")
    }

    build(json, mapping, Nil).head.asInstanceOf[A]
  }

  private def convert(value: JValue, targetType: Class[_], formats: Formats): Any = value match {
    case JInt(x) if (targetType == classOf[Int]) => x.intValue
    case JInt(x) if (targetType == classOf[Long]) => x.longValue
    case JInt(x) if (targetType == classOf[Short]) => x.shortValue
    case JInt(x) if (targetType == classOf[Byte]) => x.byteValue
    case JInt(x) if (targetType == classOf[String]) => x.toString
    case JDouble(x) if (targetType == classOf[Float]) => x.floatValue
    case JDouble(x) if (targetType == classOf[String]) => x.toString
    case JString(s) if (targetType == classOf[Date]) => formats.dateFormat.parse(s).getOrElse(fail("Invalid date '" + s + "'"))
    case JNull => null
    case JNothing => fail("Did not find value which can be converted into " + targetType.getName)
    case _ => value.values
  }
}


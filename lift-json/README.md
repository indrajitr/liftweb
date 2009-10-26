Parsing and formatting utilities for JSON.

Parsing JSON
============

Any valid json can be parsed into internal AST format.

    scala> import net.liftweb.json.JsonParser._
    scala> parse(""" { "numbers" : [1, 2, 3, 4] } """)
    res0: net.liftweb.json.JsonAST.JValue = 
          JObject(List(JField(numbers,JArray(List(JInt(1), JInt(2), JInt(3), JInt(4))))))

Producing JSON
==============

DSL rules
---------

* Primitive types map to JSON primitives.
* Any seq produces JSON array.

      scala> val json = List(1, 2, 3)

      scala> compact(JsonAST.render(json))

      res0: String = [1,2,3]

* Tuple2[String, A] produces field.

      scala> val json = ("name" -> "joe")

      scala> compact(JsonAST.render(json))

      res1: String = {"name":"joe"}

* ~ operator produces object by combining fields.

      scala> val json = ("name" -> "joe") ~ ("age" -> 35)

      scala> compact(JsonAST.render(json))

      res2: String = {"name":"joe","age":35}

* Any value can be optional. Field and value is completely removed when it doesn't have a value.

      scala> val json = ("name" -> "joe") ~ ("age" -> Some(35))

      scala> compact(JsonAST.render(json))

      res3: String = {"name":"joe","age":35}

      scala> val json = ("name" -> "joe") ~ ("age" -> (None: Option[Int]))

      scala> compact(JsonAST.render(json))

      res4: String = {"name":"joe"}

Example
-------

    object JsonExample extends Application {
      import net.liftweb.json.JsonAST
      import net.liftweb.json.JsonDSL._

      case class Winner(id: Long, numbers: List[Int])
      case class Lotto(id: Long, winningNumbers: List[Int], winners: List[Winner], drawDate: Option[java.util.Date])

      val winners = List(Winner(23, List(2, 45, 34, 23, 3, 5)), Winner(54, List(52, 3, 12, 11, 18, 22)))
      val lotto = Lotto(5, List(2, 45, 34, 23, 7, 5, 3), winners, None)

      val json = 
        ("lotto" ->
          ("lotto-id" -> lotto.id) ~
          ("winning-numbers" -> lotto.winningNumbers) ~
          ("draw-date" -> lotto.drawDate.map(_.toString)) ~
          ("winners" ->
            lotto.winners.map { w =>
              (("winner-id" -> w.id) ~
               ("numbers" -> w.numbers))}))

      println(compact(JsonAST.render(json)))
    }

    scala> JsonExample
    {"lotto":{"lotto-id":5,"winning-numbers":[2,45,34,23,7,5,3],"winners":
    [{"winner-id":23,"numbers":[2,45,34,23,3,5]},{"winner-id":54,"numbers":[52,3,12,11,18,22]}]}}

Example produces following pretty printed JSON. Notice that draw-date field is not rendered since its value is None:

    scala> pretty(JsonAST.render(JsonExample.json))

    {
      "lotto":{
        "lotto-id":5,
        "winning-numbers":[2,45,34,23,7,5,3],
        "winners":[{
          "winner-id":23,
          "numbers":[2,45,34,23,3,5]
        },{
          "winner-id":54,
          "numbers":[52,3,12,11,18,22]
        }]
      }
    }

Merging & Diffing
-----------------

Two JSONs can be merged and diffed with each other.
Please see more examples in src/test/scala/net/liftweb/json/MergeExamples.scala and src/test/scala/net/liftweb/json/DiffExamples.scala

    scala> import net.liftweb.json.JsonParser.parse
    scala> import net.liftweb.json.JsonAST._
    scala> import net.liftweb.json.Printer.pretty

    scala> val lotto1 = parse("""{
             "lotto":{
               "lotto-id":5,
               "winning-numbers":[2,45,34,23,7,5,3]
               "winners":[{
                 "winner-id":23,
                 "numbers":[2,45,34,23,3,5]
               }]
             }
           }""")

    scala> val lotto2 = parse("""{
             "lotto":{ 
               "winners":[{
                 "winner-id":54,
                 "numbers":[52,3,12,11,18,22]
               }]
             }
           }""")
    
    scala> val mergedLotto = lotto1 merge lotto2
    scala> pretty(render(mergedLotto))
    res0: String = 
    {
      "lotto":{
        "lotto-id":5,
        "winning-numbers":[2,45,34,23,7,5,3],
        "winners":[{
          "winner-id":23,
          "numbers":[2,45,34,23,3,5]
        },{
          "winner-id":54,
          "numbers":[52,3,12,11,18,22]
        }]
      }
    }

    scala> val Diff(changed, added, deleted) = mergedLotto diff lotto1
    changed: net.liftweb.json.JsonAST.JValue = JNothing
    added: net.liftweb.json.JsonAST.JValue = JNothing
    deleted: net.liftweb.json.JsonAST.JValue = JObject(List(JField(lotto,JObject(List(JField(winners,
    JArray(List(JObject(List(JField(winner-id,JInt(54)), JField(numbers,JArray(
    List(JInt(52), JInt(3), JInt(12), JInt(11), JInt(18), JInt(22))))))))))))))


Querying JSON
=============

"LINQ" style
------------

JSON values can be extracted using for-comprehensions.
Please see more examples in src/test/scala/net/liftweb/json/QueryExamples.scala

    scala> import net.liftweb.json.JsonParser.parse
    scala> import net.liftweb.json.JsonAST._
    scala> val json = parse("""
             { "name": "joe",
               "children": [
                 {
                   "name": "Mary",
                   "age": 5
                 },
                 {
                   "name": "Mazy",
                   "age": 3
                 }
               ]
             }
           """)

    scala> for { JField("age", JInt(age)) <- json } yield age
    res0: List[BigInt] = List(5, 3)

    scala> for { 
             JObject(child) <- json
             JField("name", JString(name)) <- child 
             JField("age", JInt(age)) <- child 
             if age > 4
           } yield (name, age)
    res1: List[(String, BigInt)] = List((Mary,5))

XPath + HOFs
------------

Json AST can be queried using XPath like functions. Following REPL session shows the usage of 
'\\', '\\\\', 'find', 'filter', 'map' and 'values' functions. 

    The example json is:

    { 
      "person": {
        "name": "Joe",
        "age": 35,
        "spouse": {
          "person": {
            "name": "Marilyn"
            "age": 33
          }
        }
      }
    }

    Translated to DSL syntax:

    scala> import net.liftweb.json.JsonAST._
    scala> import net.liftweb.json.JsonDSL._

    scala> val json = 
      ("person" ->
        ("name" -> "Joe") ~
        ("age" -> 35) ~
        ("spouse" -> 
          ("person" -> 
            ("name" -> "Marilyn") ~
            ("age" -> 33)
          )
        )
      )

    scala> json \\ "spouse"
    res0: net.liftweb.json.JsonAST.JValue = JObject(List(JField(spouse,JObject(List(
          JField(person,JObject(List(JField(name,JString(Marilyn)), JField(age,JInt(33))))))))))

    scala> compact(render(res0))
    res1: String = {"spouse":{"person":{"name":"Marilyn","age":33}}}

    scala> compact(render(json \\ "name"))
    res2: String = {"name":"Joe","name":"Marilyn"}

    scala> compact(render(json \ "person" \ "name"))
    res3: String = "name":"Joe"

    scala> compact(render(json \ "person" \ "spouse" \ "person" \ "name"))
    res4: String = "name":"Marilyn"

    scala> json find {
             case JField("name", _) => true
             case _ => false
           }
    res5: Option[net.liftweb.json.JsonAST.JValue] = Some(JField(name,JString(Joe)))

    scala> json filter {
             case JField("name", _) => true
             case _ => false
           }
    res6: List[net.liftweb.json.JsonAST.JValue] = List(JField(name,JString(Joe)), JField(name,JString(Marilyn)))

    scala> json map {
             case JField("name", JString(s)) => JField("NAME", JString(s.toUpperCase))
             case x => x
           }
    res7: net.liftweb.json.JsonAST.JValue = JObject(List(JField(person,JObject(List(
    JField(NAME,JString(JOE)), JField(age,JInt(35)), JField(spouse,JObject(List(
    JField(person,JObject(List(JField(NAME,JString(MARILYN)), JField(age,JInt(33)))))))))))))

    scala> json.values
    res8: net.liftweb.json.JsonAST.JValue#Values = Map(person -> Map(name -> Joe, age -> 35, spouse -> Map(person -> Map(name -> Marilyn, age -> 33))))

Indexed path expressions work too.

    scala> val json = parse("""
             { "name": "joe",
               "children": [
                 {
                   "name": "Mary",
                   "age": 5
                 },
                 {
                   "name": "Mazy",
                   "age": 3
                 }
               ]
             }
           """)

    scala> (json \ "children")(0)
    res0: net.liftweb.json.JsonAST.JValue = JObject(List(JField(name,JString(Mary)), JField(age,JInt(5))))

    scala> (json \ "children")(1) \ "name"
    res1: net.liftweb.json.JsonAST.JValue = JField(name,JString(Mazy))

Extracting values
=================

Case classes can be used to extract values from parsed JSON. Non-existing values
can be extracted into scala.Option and strings can be automatically converted into
java.util.Dates.
Please see more examples in src/test/scala/net/liftweb/json/ExtractionExamples.scala

    scala> implicit val formats = net.liftweb.json.DefaultFormats // Brings in default date formats etc.
    scala> case class Child(name: String, age: Int, birthdate: Option[java.util.Date])
    scala> case class Address(street: String, city: String)
    scala> case class Person(name: String, address: Address, children: List[Child])
    scala> import net.liftweb.json.JsonParser._
    scala> val json = parse("""
             { "name": "joe",
               "address": {
                 "street": "Bulevard",
                 "city": "Helsinki"
               },
               "children": [
                 {
                   "name": "Mary",
                   "age": 5
                   "birthdate": "2004-09-04T18:06:22Z"
                 },
                 {
                   "name": "Mazy",
                   "age": 3
                 }
               ]
             }
           """)

    scala> json.extract[Person] 
    res0: Person = Person(joe,Address(Bulevard,Helsinki),List(Child(Mary,5,Some(Sat Sep 04 18:06:22 EEST 2004)), Child(Mazy,3,None)))

By default the constructor parameter names must match json field names. However, sometimes json 
field names contain characters which are not allowed characters in Scala identifiers. There's two 
solutions for this (see src/test/scala/net/liftweb/json/LottoExample.scala for bigger example).

1. Use back ticks.

    scala> case class Person(\`first-name\`: String)

2. Use map function to postprocess AST.

    scala> case class Person(firstname: String)
    scala> json map {
             case JField("first-name", x) => JField("firstname", x)
             case x => x
           }

Serialization
=============

Case classes can be serialized and deserialized.
Please see other examples in src/test/scala/net/liftweb/json/SerializationExamples.scala

    scala> import net.liftweb.json._
    scala> import net.liftweb.json.Serialization.{read, write}
    scala> implicit val formats = Serialization.formats(NoTypeHints)
    scala> val ser = write(Child("Mary", 5, None))
    scala> read[Child](ser)
    res1: Child = Child(Mary,5,None)

Serialization supports:

* Arbitrarily deep case class graphs
* All primitive types, including BigInts
* Lists
* scala.Option
* java.util.Date
* Polymorphic Lists (see below)

It does not support:

* Java serialization (classes marked with @serializable annotation etc.)
* Maps, Sets or other collection types, just Lists and Options for now
* Multidimensional Lists

Serializing polymorphic Lists
-----------------------------

Type hints are required when serializing polymorphic (or heterogeneous) Lists. Serialized JSON objects
will get an extra field named 'jsonClass'.

    scala> trait Animal
    scala> case class Dog(name: String) extends Animal
    scala> case class Fish(weight: Double) extends Animal
    scala> case class Animals(animals: List[Animal])
    scala> implicit val formats = Serialization.formats(ShortTypeHints(List(classOf[Dog], classOf[Fish])))
    scala> val ser = write(Animals(Dog("pluto") :: Fish(1.2) :: Nil))
    ser: String = {"animals":[{"jsonClass":"Dog","name":"pluto"},{"jsonClass":"Fish","weight":1.2}]}

    scala> read[Animals](ser)
    res0: Animals = Animals(List(Dog(pluto), Fish(1.2)))

ShortTypeHints outputs short classname for all instances of configured objects. FullTypeHints outputs full
classname. Other strategies can be implemented by extending TypeHints trait.

XML support
===========

JSON structure can be converted to XML node and vice versa.
Please see more examples in src/test/scala/net/liftweb/json/XmlExamples.scala

    scala> import net.liftweb.json.Xml.{toJson, toXml}
    scala> val xml =
             <users>
               <user>
                 <id>1</id>
                 <name>Harry</name>
               </user>
               <user>
                 <id>2</id>
                 <name>David</name>
               </user>
             </users>   

    scala> val json = toJson(xml)
    scala> pretty(render(json))
    res3: {
      "users":{
        "user":[{
          "id":"1",
          "name":"Harry"
        },{
          "id":"2",
          "name":"David"
        }]
      }
    }

Now, the above example has two problems. First, the id is converted to String while we might want it as an Int. This
is easy to fix by mapping JString(s) to JInt(s.toInt). The second problem is more subtle. The conversion function
decides to use JSON array because there's more than one user-element in XML. Therefore a structurally equivalent
XML document which happens to have just one user-element will generate a JSON document without JSON array. This
is rarely a desired outcome. These both problems can be fixed by following map function.

    scala> json map {
             case JField("id", JString(s)) => JField("id", JInt(s.toInt))
             case JField("user", x: JObject) => JField("user", JArray(x :: Nil))
             case x => x 
           }

Other direction is supported too. Converting JSON to XML:

     scala> toXml(json)
     res5: scala.xml.NodeSeq = <users><user><id>1</id><name>Harry</name></user><user><id>2</id><name>David</name></user></users>


Kudos
=====

* The original idea for DSL syntax was taken from Lift mailing list ([by Marius](http://markmail.org/message/lniven2hn22vhupu)).

* The idea for AST and rendering was taken from [Real World Haskell book](http://book.realworldhaskell.org/read/writing-a-library-working-with-json-data.html).

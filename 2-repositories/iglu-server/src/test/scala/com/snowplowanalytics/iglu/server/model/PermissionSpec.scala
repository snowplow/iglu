package com.snowplowanalytics.iglu.server.model

import com.snowplowanalytics.iglu.server.model.Permission.{SchemaAction, Vendor, KeyAction}

class PermissionSpec extends org.specs2.Specification { def is = s2"""
  canRead returns true for read-only key $e1
  Vendor.check returns true for exactly same vendor $e2
  Vendor.check returns false for child vendor without wildcard $e3
  canCreatePermission returns false if original permission has no keyActions $e4
  canCreatePermission returns true if original permission has Create $e5
  canCreatePermission returns false if source vendor does not match $e6
  canCreatePermission returns true for super-write key $e7
  Vendor.parse parses empty string as wildcard $e8
  """

  def e1 = {
    val permission = Permission(Vendor(List("com", "acme"), false), Some(SchemaAction.Read), Set.empty)
    permission.canRead("com.acme") must beTrue
  }

  def e2 = {
    Vendor(List("com", "acme"), false).check("com.acme") must beTrue
  }

  def e3 = {
    Vendor(List("com", "acme"), false).check("com.acme.unrelated") must beFalse
  }

  def e4 = {
    val permission = Permission(Vendor(List("com", "acme"), false), Some(SchemaAction.Read), Set.empty)
    permission.canCreatePermission("com.acme") must beFalse
  }

  def e5 = {
    val permission = Permission(Vendor(List("com", "acme"), false), Some(SchemaAction.Read), Set(KeyAction.Create))
    permission.canCreatePermission("com.acme") must beTrue
  }

  def e6 = {
    val permission = Permission(Vendor(List("com", "acme"), false), Some(SchemaAction.Read), Set(KeyAction.Create))
    permission.canCreatePermission("com.foo") must beFalse
  }

  def e7 = {
    val permission = Permission(Vendor(List(),true),Some(SchemaAction.CreateVendor),Set())
    permission.canCreateSchema("com.snowplowanalytics.snowplow.storage") must beTrue
  }

  def e8 = {
    Vendor.parse(" ") must beEqualTo(Vendor.wildcard)
  }
}

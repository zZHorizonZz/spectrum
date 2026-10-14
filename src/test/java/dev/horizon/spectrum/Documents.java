package dev.horizon.spectrum;

final class Documents {

  static final String MODEL = """
      model
        schema 1.1

      type user

      type group
        relations
          define member: [user, user:*, group#member]

      type folder
        relations
          define owner: [user]
          define parent: [folder]
          define viewer: [user, group#member] or owner or viewer from parent

      type doc
        relations
          define parent: [folder]
          define owner: [user]
          define editor: [user, group#member] or owner
          define viewer: [user, user:*, group#member] or editor or viewer from parent
          define blocked: [user]
          define reader: viewer but not blocked
          define approver: [user]
          define publisher: editor and approver
      """;

  static final String[] TUPLES = {
      "group:eng#member@user:anne",
      "group:eng#member@group:design#member",
      "group:design#member@user:beth",
      "group:all#member@user:*",
      "folder:root#owner@user:carl",
      "folder:root#viewer@group:eng#member",
      "folder:sub#parent@folder:root",
      "doc:plan#parent@folder:sub",
      "doc:plan#owner@user:dana",
      "doc:plan#approver@user:dana",
      "doc:plan#blocked@user:beth",
      "doc:memo#viewer@user:*",
      "doc:memo#blocked@user:erin",
      "doc:wiki#editor@group:all#member" };

  private Documents() {
  }
}

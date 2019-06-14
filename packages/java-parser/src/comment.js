"use strict";
const JavaParser = require("./parser");

class JavaCommentParser extends JavaParser {
  constructor() {
    super();
    this.comments = [];
    this.cursor = 0;
    this.leadingComments = {};
    this.trailingComments = {};
  }

  findUpperBoundToken(comment) {
    let diff;
    let i;
    let current;

    let len = this.input.length;
    i = 0;

    while (len) {
      diff = len >>> 1;
      current = i + diff;
      if (this.input[current].startOffset > comment.startOffset) {
        len = diff;
      } else {
        i = current + 1;
        len -= diff + 1;
      }
    }
    return i;
  }

  extendCommentRange(comments) {
    this.comments = comments;
    let position;
    this.comments.forEach(comment => {
      position = this.findUpperBoundToken(comment);
      comment.extendedRange = {};
      comment.extendedRange.startOffset =
        position - 1 < 0
          ? comment.startOffset
          : this.input[position - 1].endOffset;
      comment.extendedRange.endOffset =
        position == this.input.length
          ? comment.endOffset
          : this.input[position].startOffset;
    });
  }

  cstPostNonTerminal(node, ruleCstResult, ruleName) {
    if (this.isBackTracking() === false) {
      super.cstPostNonTerminal(node, ruleCstResult, ruleName);

      this.comments.forEach(comment => {
        if (comment.extendedRange.endOffset === node.location.startOffset) {
          this.leadingComments[comment.startOffset] = node;
        } else if (
          comment.extendedRange.startOffset === node.location.endOffset
        ) {
          this.trailingComments[comment.endOffset] = node;
        }
      });
    }
  }

  attachComments() {
    this.comments.forEach(comment => {
      if (this.leadingComments[comment.startOffset]) {
        if (!this.leadingComments[comment.startOffset].leadingComments) {
          this.leadingComments[comment.startOffset].leadingComments = [];
        }
        this.leadingComments[comment.startOffset].leadingComments.push(comment);
      }
      if (this.trailingComments[comment.endOffset]) {
        if (!this.trailingComments[comment.endOffset].trailingComments) {
          this.trailingComments[comment.endOffset].trailingComments = [];
        }
        this.trailingComments[comment.endOffset].trailingComments.push(comment);
      }
    });
  }

  isEmpty(node) {
    if (!node || !node.children) {
      return true;
    }
    Object.keys(node.children).forEach(key => {
      if (!node.children[key][0].tokenType) {
        return false;
      }
    });
    return true;
  }
}

module.exports = JavaCommentParser;

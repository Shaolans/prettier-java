"use strict";
const JavaParser = require("./parser");

class JavaCommentParser extends JavaParser {
  constructor() {
    super();
    this.comments = [];
    this.cursor = 0;
    this.leadingComments = {};
  }

  deepCopy(src) {
    return JSON.parse(JSON.stringify(src));
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

  initCommentsProcess(comments) {
    this.comments = this.deepCopy(comments);
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

      //node.childrenArr = [];
      let startOffset = Number.POSITIVE_INFINITY;
      let endOffset = Number.NEGATIVE_INFINITY;
      if (node.hasOwnProperty("children")) {
        for (const key in node.children) {
          node.children[key].forEach(child => {
            //node.childrenArr.push(child);

            if (child.startOffset < startOffset) {
              startOffset = child.startOffset;
            }
            if (child.endOffset > endOffset) {
              endOffset = child.endOffset;
            }
          });
        }
      }
      node.startOffset = startOffset;
      node.endOffset = endOffset;

      /*
      node.childrenArr.sort((child1, child2) => {
        return child1.startOffset < child2.startOffset ? -1 : 1;
      });*/

      this.comments.forEach(comment => {
        if (comment.extendedRange.endOffset === node.startOffset) {
          this.leadingComments[comment.startOffset] = node;
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
    });
  }
}

module.exports = JavaCommentParser;

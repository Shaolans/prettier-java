"use strict";
const JavaParser = require("./parser");

class JavaCommentParser extends JavaParser {
  constructor() {
    super();
  }

  cstPostNonTerminal(node, ruleCstResult, ruleName) {
    if (this.isBackTracking() === false) {
      super.cstPostNonTerminal(node, ruleCstResult, ruleName);

      node.childrenArr = [];
      let startOffset = Number.POSITIVE_INFINITY;
      let endOffset = Number.NEGATIVE_INFINITY;
      if (node.hasOwnProperty("children")) {
        for (const key in node.children) {
          node.children[key].forEach(child => {
            node.childrenArr.push(child);

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

      node.childrenArr.sort((child1, child2) => {
        return child1.startOffset < child2.startOffset ? -1 : 1;
      });
    }
  }
}

module.exports = JavaCommentParser;

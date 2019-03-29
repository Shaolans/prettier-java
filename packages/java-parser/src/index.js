/* eslint no-console: 0 */
"use strict";
const JavaLexer = require("./lexer");
const JavaCommentParser = require("./comments");

// const startTime = new Date().getTime();
const parser = new JavaCommentParser();
const BaseJavaCstVisitor = parser.getBaseCstVisitorConstructor();
const BaseJavaCstVisitorWithDefaults = parser.getBaseCstVisitorConstructorWithDefaults();

// const endTime = new Date().getTime();
// const totalTime = endTime - startTime;
// console.log("parse start time (ms): " + totalTime);

function parse(inputText, entryPoint = "compilationUnit") {
  // Lex
  const lexResult = JavaLexer.tokenize(inputText);
  parser.input = lexResult.tokens;

  if (lexResult.errors.length > 0) {
    const firstError = lexResult.errors[0];
    throw Error(
      "Sad sad panda, lexing errors detected in line: " +
        firstError.line +
        ", column: " +
        firstError.column +
        "!\n" +
        firstError.message
    );
  }

  parser.initCommentsProcess(lexResult.groups.comments);

  // Automatic CST created when parsing
  const cst = parser[entryPoint]();
  if (parser.errors.length > 0) {
    const error = parser.errors[0];
    throw Error(
      "Sad sad panda, parsing errors detected in line: " +
        error.token.startLine +
        ", column: " +
        error.token.startColumn +
        "!\n" +
        error.message +
        "!\n\t->" +
        error.context.ruleStack.join("\n\t->")
    );
  }

  parser.attachComments();
  console.log(JSON.stringify(cst));
  return cst;
}

module.exports = {
  parse,
  BaseJavaCstVisitor,
  BaseJavaCstVisitorWithDefaults
};

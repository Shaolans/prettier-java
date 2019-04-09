"use strict";

const javaParser = require("../../java-parser/src/index");

function parse(text) {
  const cst = javaParser.parse(text);
  /*
  console.log(JSON.stringify(cst, function(key, value){
    if(key !== 'START_CHARS_HINT'){
      return value;
    }
  }))*/
  return cst;
}

module.exports = parse;

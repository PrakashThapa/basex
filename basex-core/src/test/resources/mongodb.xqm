module namespace _ = '_';
import module namespace n ="http://basex.org/modules/nosql/MongoDB";


(:~ Function demonstrating a successful test. :)
declare %unit:test function _:assert-success() {
  unit:assert(<a/>)
};
  
(:~ Function demonstrating an expected failure. :)
declare %unit:test("expected", "FORG0001") function _:expected-failure() {
  1 + <a/>
};
  
(:~ Function demonstrating an expected failure. :)
declare %unit:test("expected", "FORG0001") function _:connect() {
  let $url := ""
  return n:connect({})
};

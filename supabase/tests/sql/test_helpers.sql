create or replace function test.try(p text) returns text language plpgsql as $$
begin execute p; return 'OK'; exception when others then return 'ERR: ' || sqlerrm; end $$;
grant execute on function test.try(text) to anon, authenticated;
grant execute on all functions in schema test to anon, authenticated;
grant select on test.ids to anon, authenticated;

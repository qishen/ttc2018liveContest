use std::path::Path;
use std::fs;
use std::sync::Arc;

use differential_formula::engine::*;
use differential_formula::term::*;


fn main() {
    let change_path = std::env::var("ChangePath").unwrap_or("None".to_string());
    let run_index = std::env::var("RunIndex").unwrap_or("None".to_string());
    let sequences = std::env::var("Sequences").unwrap_or("20".to_string()).parse::<usize>().expect("Couldn't parse Sequences as an integer");
    let change_set = std::env::var("ChangeSet").unwrap_or("None".to_string());
    let query = std::env::var("Query").unwrap_or("Q2".to_string());
    let tool = std::env::var("Tool").unwrap_or("None".to_string());

    // Prepare input path information.
    let mut path = std::env::args().nth(1).expect("Must describe path");
    if path.as_str() == "HARNESS" {
        path = format!("{}/", change_path);
    }

    let mut timer = std::time::Instant::now();

    // Load SocialNetwork doamin into FORMULA engine.
    let domain_path = Path::new("./src/SocialNetwork.4ml");
    let content = fs::read_to_string(domain_path).unwrap();

    let mut engine = DDEngine::new();
    let env = DDEngine::parse_string(content);
    engine.install(env);

    // Each session has one specific domain attached and model is optional.
    let mut session = engine.create_session("SocialNetwork", None);

    println!("{:?};{:?};{};{};0;\"Initialization\";\"Time\";{}", tool, query, change_set, run_index, timer.elapsed().as_millis());
    timer = std::time::Instant::now();

    let comms = load_data(&format!("{}csv-comments-initial.csv", path));
    let knows = load_data(&format!("{}csv-friends-initial.csv", path));
    let likes = load_data(&format!("{}csv-likes-initial.csv", path));
    let posts = load_data(&format!("{}csv-posts-initial.csv", path));
    let users = load_data(&format!("{}csv-users-initial.csv", path));

    println!("{:?};{:?};{};{};0;\"Load\";\"Time\";{}", tool, query, change_set, run_index, timer.elapsed().as_millis());
    timer = std::time::Instant::now();

    let mut term_collection = vec![];

    for comment in comms {
        term_collection.push(strings_to_comm(comment, &session));
    }

    for know in knows {
        term_collection.push(strings_to_know(know, &session));
    }

    for like in likes {
        term_collection.push(strings_to_like(like, &session));
    }

    for post in posts {
        term_collection.push(strings_to_post(post, &session));
    }

    for user in users {
        term_collection.push(strings_to_user(user, &session));
    }

    session.add_terms(term_collection);

    println!("Execution time: {}", timer.elapsed().as_millis());


    for round in 1 .. (sequences + 1) {

        // Insert new records!
        let filename = format!("{}change{:02}.csv", path, round);
        let changes = load_data(&filename);
        let mut term_change_collection = vec![];

        for mut change in changes {
            let collection = change.remove(0);
            match collection.as_str() {
                "Comments" => { term_change_collection.push(strings_to_comm(change, &session)); },
                "Friends" => { term_change_collection.push(strings_to_know(change, &session)); },
                "Likes" => { term_change_collection.push(strings_to_like(change, &session)); },
                "Posts" => { term_change_collection.push(strings_to_post(change, &session)); },
                "Users" => { term_change_collection.push(strings_to_user(change, &session)); },
                x => { panic!("Weird enum variant: {}", x); },
            }
        }

        session.add_terms(term_change_collection);
    }
}



fn load_data(filename: &str) -> Vec<Vec<String>> {

    // Standard io/fs boilerplate.
    use std::io::{BufRead, BufReader};
    use std::fs::File;

    let mut data = Vec::new();
    let file = BufReader::new(File::open(filename).expect("Could open file"));
    let lines = file.lines();

    for (_, readline) in lines.enumerate() {
        if let Ok(line) = readline {
            let text : Vec<String> =
            line.split('|')
                .map(|x| x.to_string())
                .collect();

            data.push(text);
        }
    }
    data
}


fn strings_to_comm(comm: Vec<String>, session: &Session) -> Arc<Term> {
    let mut iter = comm.into_iter();
    let id = iter.next().unwrap();
    let ts = iter.next().unwrap();
    let mut split = ts.split_whitespace();
    let date = split.next().unwrap();
    let time = split.next().unwrap();
    // Convert data and time into a timestamp as an integer.
    let ts = format!("{}T{}+00:00", date, time);
    let ts = chrono::DateTime::parse_from_rfc3339(ts.as_str()).expect("Failed to parse DateTime").timestamp();
    let content = iter.next().unwrap();
    let creator = iter.next().unwrap();
    let parent = iter.next().unwrap();
    let post = iter.next().unwrap();

    let comment_str = format!("Comments({:?}, {:?}, {:?}, {:?}, {:?}, {:?})", id, ts, content, creator, parent, post);
    session.parse_term_str(&comment_str[..]).unwrap()
}

fn strings_to_know(know: Vec<String>, session: &Session) -> Arc<Term> {
    let mut iter = know.into_iter();
    let person1 = iter.next().unwrap();
    let person2 = iter.next().unwrap();
    
    let friend_str = format!("Friend({:?}, {:?})", person1, person2);
    session.parse_term_str(&friend_str[..]).unwrap()
}

fn strings_to_like(like: Vec<String>, session: &Session) -> Arc<Term> {
    let mut iter = like.into_iter();
    let person = iter.next().unwrap();
    let comment = iter.next().unwrap();
    
    let likes_str = format!("Likes({:?}, {:?})", person, comment);
    session.parse_term_str(&likes_str[..]).unwrap()
}

fn strings_to_post(post: Vec<String>, session: &Session) -> Arc<Term> {
    let mut iter = post.into_iter();
    let id = iter.next().unwrap();
    let ts = iter.next().unwrap();
    let mut split = ts.split_whitespace();
    let date = split.next().unwrap();
    let time = split.next().unwrap();
    let ts = format!("{}T{}+00:00", date, time);
    let ts = chrono::DateTime::parse_from_rfc3339(ts.as_str()).expect("Failed to parse DateTime").timestamp();
    let content = iter.next().unwrap();
    let creator = iter.next().unwrap();

    let post_str = format!("Posts({:?}, {:?}, {:?}, {:?})", id, ts, content, creator);
    session.parse_term_str(&post_str[..]).unwrap()
}

fn strings_to_user(user: Vec<String>, session: &Session) -> Arc<Term> {
    let mut iter = user.into_iter();
    let person = iter.next().unwrap();
    let name = iter.next().unwrap();

    let user_str = format!("User({:?}, {:?})", person, name);
    session.parse_term_str(&user_str[..]).unwrap()
}

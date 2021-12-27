database example {

  schema bookshelf {

    table dude {
      name varchar(100)
    }

    table author {
      name varchar(100)
    }

    table book {
      title varchar(100)
    }

    table borrowing {
      reservation_date date?
      lending_date date?
      restitution_date date?
    }

    author *-* book
    borrowing -> book
    borrowing -> dude

  }

}
